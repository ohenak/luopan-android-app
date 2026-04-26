package com.luopan.compass.magnetic

import android.content.Context
import com.luopan.compass.util.Clock
import java.io.File
import java.io.InputStream
import java.io.Reader
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.*

/**
 * Implements [MagneticFieldModel] using the bundled WMM2025 Gauss coefficients.
 *
 * Implements the NOAA WMM spherical harmonic synthesis algorithm following the
 * standard WMM technical note (NOAA TR-53). Uses the WGS-84 ellipsoid for
 * geodetic-to-geocentric coordinate conversion and Schmidt quasi-normalized
 * associated Legendre polynomials.
 *
 * Coefficients are loaded eagerly in the constructor. Throws [WmmLoadException] on failure.
 */
class Wmm2025Model private constructor(
    private val clock: Clock,
    private val epochRef: Double,          // reference epoch from COF header (e.g. 2025.0)
    private val g: Array<DoubleArray>,     // g[n][m], n in 1..MAX_N, m in 0..n (nT)
    private val h: Array<DoubleArray>,     // h[n][m] (nT)
    private val gSv: Array<DoubleArray>,   // secular variation dg/dt [n][m] (nT/year)
    private val hSv: Array<DoubleArray>    // secular variation dh/dt [n][m] (nT/year)
) : MagneticFieldModel {

    companion object {
        const val MAX_N = 12
        private const val EPOCH_START = 2025.0
        private const val EPOCH_END = 2030.0

        /**
         * WMM reference sphere radius in km (matches the a value used in the COF convention).
         * This is the standard WMM reference radius — NOT the WGS-84 semi-major axis.
         */
        private const val WMM_REFERENCE_RADIUS_KM = 6371.2

        /**
         * Construct from an Android [Context], loading from R.raw.wmm2025_cof.
         */
        fun fromContext(context: Context, clock: Clock): Wmm2025Model {
            val stream = try {
                val resId = context.resources.getIdentifier("wmm2025_cof", "raw", context.packageName)
                context.resources.openRawResource(resId)
            } catch (e: Exception) {
                throw WmmLoadException("Failed to open wmm2025_cof resource", e)
            }
            return stream.bufferedReader(Charsets.UTF_8).use { fromReader(it, clock) }
        }

        /** Construct from a [File] — for unit tests where Android resources are unavailable. */
        fun fromFile(file: File, clock: Clock): Wmm2025Model {
            return file.bufferedReader(Charsets.UTF_8).use { fromReader(it, clock) }
        }

        /** Construct from an [InputStream]. */
        fun fromStream(stream: InputStream, clock: Clock): Wmm2025Model {
            return stream.bufferedReader(Charsets.UTF_8).use { fromReader(it, clock) }
        }

        private fun fromReader(reader: Reader, clock: Clock): Wmm2025Model {
            val lines = reader.readLines()
                .map { it.trim() }
                .filter { it.isNotBlank() && !it.startsWith("#") }

            if (lines.isEmpty()) throw WmmLoadException("COF file is empty or all comments")

            // Header line: "  2025.0  WMM-2025  12/31/2024"
            val headerTokens = lines[0].split("\\s+".toRegex()).filter { it.isNotBlank() }
            val epochRef = headerTokens.firstOrNull()?.toDoubleOrNull()
                ?: throw WmmLoadException("Cannot parse epoch from COF header: ${lines[0]}")

            val g   = Array(MAX_N + 1) { DoubleArray(MAX_N + 1) }
            val h   = Array(MAX_N + 1) { DoubleArray(MAX_N + 1) }
            val gSv = Array(MAX_N + 1) { DoubleArray(MAX_N + 1) }
            val hSv = Array(MAX_N + 1) { DoubleArray(MAX_N + 1) }

            for (i in 1 until lines.size) {
                val line = lines[i]
                if (line.startsWith("999")) break
                val tokens = line.split("\\s+".toRegex()).filter { it.isNotBlank() }
                if (tokens.size < 6) continue
                try {
                    val n = tokens[0].toInt()
                    val m = tokens[1].toInt()
                    if (n < 1 || n > MAX_N || m < 0 || m > n) continue
                    g[n][m]   = tokens[2].toDouble()
                    h[n][m]   = tokens[3].toDouble()
                    gSv[n][m] = tokens[4].toDouble()
                    hSv[n][m] = tokens[5].toDouble()
                } catch (e: NumberFormatException) {
                    throw WmmLoadException("Failed to parse COF line $i: $line", e)
                }
            }

            return Wmm2025Model(clock, epochRef, g, h, gSv, hSv)
        }
    }

    // ─── MagneticFieldModel interface ────────────────────────────────────────

    override fun getDeclination(latDeg: Double, lonDeg: Double, altM: Double, epochYears: Double): Float =
        evaluate(latDeg, lonDeg, altM, epochYears).declination

    override fun getExpectedInclination(latDeg: Double, lonDeg: Double, altM: Double, epochYears: Double): Float =
        evaluate(latDeg, lonDeg, altM, epochYears).inclination

    override fun getExpectedFieldMagnitude(latDeg: Double, lonDeg: Double, altM: Double, epochYears: Double): Float =
        evaluate(latDeg, lonDeg, altM, epochYears).totalField

    override fun getModelId(): String = "WMM2025"

    override fun isExpired(): Boolean {
        val epochYears = clock.toEpochYears()
        return epochYears < EPOCH_START || epochYears >= EPOCH_END
    }

    // ─── Spherical Harmonic Synthesis ────────────────────────────────────────

    /**
     * Evaluates the WMM2025 model and returns all three field components.
     *
     * Algorithm reference: NOAA WMM2025 Technical Note (TR-53), Chapter 4.
     *
     * Summary of coordinate conventions:
     * - theta = geocentric colatitude (0° at N pole, 180° at S pole)
     * - P(n,m)(cosTheta) = Schmidt quasi-normalized associated Legendre polynomial
     * - B_r     = radially outward field component (nT, positive outward)
     * - B_theta = southward field component (nT, positive toward S pole); theta increases southward
     * - B_phi   = eastward field component (nT, positive east)
     * - Geodetic NED: X=northward, Y=eastward, Z=downward (toward Earth center)
     *   X = -B_theta (geocentric) after rotation; Z = -B_r (geocentric) after rotation
     */
    fun evaluate(latDeg: Double, lonDeg: Double, altM: Double, epochYears: Double): WmmResult {

        // ── 1. Apply secular variation ────────────────────────────────────────
        val dt = epochYears - epochRef
        val gT = Array(MAX_N + 1) { n -> DoubleArray(MAX_N + 1) { m -> g[n][m] + dt * gSv[n][m] } }
        val hT = Array(MAX_N + 1) { n -> DoubleArray(MAX_N + 1) { m -> h[n][m] + dt * hSv[n][m] } }

        // ── 2. Geodetic to geocentric spherical coordinates ───────────────────
        // WGS-84 ellipsoid
        val a_wgs  = 6378.137            // km, semi-major axis
        val b_wgs  = 6356.752314245      // km, semi-minor axis = a*(1-f)
        val e2_wgs = 1.0 - (b_wgs / a_wgs).pow(2)   // first eccentricity squared

        val phiRad = Math.toRadians(latDeg)    // geodetic latitude (radians)
        val lamRad = Math.toRadians(lonDeg)    // longitude (radians)
        val h_km   = altM / 1000.0             // altitude in km

        val sinPhi = sin(phiRad)
        val cosPhi = cos(phiRad)

        // Prime vertical radius of curvature (km)
        val N_curv = a_wgs / sqrt(1.0 - e2_wgs * sinPhi * sinPhi)

        // Geocentric Cartesian XZ (Y=0 in meridional plane; full 3D not needed)
        val xCart = (N_curv + h_km) * cosPhi
        val zCart = (N_curv * (1.0 - e2_wgs) + h_km) * sinPhi

        // Geocentric radius and geocentric latitude
        val r      = sqrt(xCart * xCart + zCart * zCart)
        val phiGc  = asin(zCart / r)    // geocentric latitude (radians)

        // ── 3. Legendre polynomials with colatitude argument ──────────────────
        // theta = geocentric colatitude; cosT = cos(theta) = sin(phiGc); sinT = sin(theta) = cos(phiGc)
        val cosT = sin(phiGc)     // = sin(geocentric lat)
        val sinT = cos(phiGc)     // = cos(geocentric lat); always ≥ 0 since |phiGc| ≤ 90°

        val P = schmidtP(cosT, sinT)   // P[n][m] for n in 0..MAX_N

        // ── 4. Spherical harmonic synthesis in geocentric frame ───────────────
        //
        // Magnetic scalar potential (NOAA WMM convention):
        //   V = a * Σ_{n=1}^{N} (a/r)^{n+1} * Σ_{m=0}^{n} P(n,m)(cosT) * [gnm*cos(m*lam) + hnm*sin(m*lam)]
        //
        // Field components (B = -∇V in spherical coordinates):
        //   B_r     = -∂V/∂r          = +Σ (n+1) * (a/r)^{n+2} * Σ P * gnmC   (positive outward)
        //   B_theta = -(1/r) * ∂V/∂θ  = +Σ (a/r)^{n+2} * Σ (∂P/∂θ) * gnmC    (positive southward)
        //   B_phi   = -(1/(r sinθ)) * ∂V/∂λ
        //           = +Σ (a/r)^{n+2} * Σ m * P * gnmS / sinθ                  (positive eastward)
        //
        // where:
        //   gnmC = gT[n][m]*cos(m*λ) + hT[n][m]*sin(m*λ)   (cosine combination)
        //   gnmS = hT[n][m]*cos(m*λ) - gT[n][m]*sin(m*λ)   (sine combination, from ∂(gnmC)/∂λ / m)
        //          Note: ∂(gnmC)/∂λ = m * [-gT*sin + hT*cos] = m * gnmS
        //
        // Check gnmS sign: ∂V/∂λ uses m*(-gnm*sin + hnm*cos), then B_phi = -(1/(r sinθ))*∂V/∂λ
        //   ∂V/∂λ = a * Σ (a/r)^{n+1} * Σ P * m * [-gnm*sin(mλ) + hnm*cos(mλ)]
        //   B_phi = -(1/(r sinθ)) * ∂V/∂λ = +(1/(r sinθ)) * a * Σ (a/r)^{n+1} * ... * m * [gnm*sin - hnm*cos]
        //         Wait — let me re-derive:
        //   B_phi = -(∂V/∂λ) / (r sinθ)
        //   ∂(gnmC)/∂λ = -m * gT*sin(mλ) + m * hT*cos(mλ) = m * [-gT*sin + hT*cos]
        //   So ∂V/∂λ = a * Σ (a/r)^{n+1} * Σ P * m * [-gT*sin + hT*cos]
        //   B_phi = -(1/(r sinθ)) * a * Σ (a/r)^{n+1} * Σ P * m * [-gT*sin + hT*cos]
        //         = +(1/(r sinθ)) * a * Σ (a/r)^{n+1} * Σ P * m * [gT*sin - hT*cos]
        //   Note a*(a/r)^{n+1}/r = (a/r)^{n+2}, so:
        //   B_phi = +Σ (a/r)^{n+2} * Σ P * m * [gT[n][m]*sin(mλ) - hT[n][m]*cos(mλ)] / sinθ
        //
        // Let gnmS2 = gT[n][m]*sin(mλ) - hT[n][m]*cos(mλ)
        // Then B_phi = +Σ (a/r)^{n+2} * Σ P * m * gnmS2 / sinθ

        val ratio = WMM_REFERENCE_RADIUS_KM / r

        var Br     = 0.0    // outward radial (nT)
        var Btheta = 0.0    // southward (nT)
        var Bphi   = 0.0    // eastward (nT)

        for (n in 1..MAX_N) {
            val rn2 = ratio.pow(n + 2)    // (a/r)^{n+2}

            for (m in 0..n) {
                val cosMLam = cos(m * lamRad)
                val sinMLam = sin(m * lamRad)

                val gnmC  = gT[n][m] * cosMLam + hT[n][m] * sinMLam   // for B_r, B_theta
                val gnmS2 = gT[n][m] * sinMLam - hT[n][m] * cosMLam   // for B_phi

                val Pnm  = P[n][m]
                val dPnm = schmidtdP(P, n, m, cosT, sinT)

                // B_r = -∂V/∂r = +Σ (n+1)*(a/r)^{n+2} * P * gnmC  (positive outward)
                Br += (n + 1) * rn2 * gnmC * Pnm

                // B_theta = -(1/r)*∂V/∂θ = -Σ (a/r)^{n+2} * (∂P/∂θ) * gnmC  (positive southward)
                Btheta -= rn2 * gnmC * dPnm

                // B_phi = -(1/(r sinθ))*∂V/∂λ = +Σ (a/r)^{n+2} * m * [gnm*sin-hnm*cos] * P / sinθ
                // gnmS2 = gT[n][m]*sin(mλ) - hT[n][m]*cos(mλ)  (derived above)
                if (sinT > 1e-10) {
                    Bphi += rn2 * m * gnmS2 * Pnm / sinT
                }
            }
        }

        // ── 5. Rotate from geocentric to geodetic NED ─────────────────────────
        //
        // In the geocentric frame the NED triplet is:
        //   X_gc (northward geocentric) = -B_theta  (negative because theta points SOUTH)
        //   Y_gc (eastward)             = B_phi
        //   Z_gc (downward geocentric)  = -B_r       (negative because B_r points OUTWARD)
        //
        // The geodetic NED frame is rotated by psi = phiGc - phiRad from the geocentric frame
        // around the eastward (Y) axis:
        //   X_geo = X_gc * cos(psi) + Z_gc * sin(psi)     (northward geographic)
        //   Z_geo = -X_gc * sin(psi) + Z_gc * cos(psi)    (downward geographic)
        //   Y_geo = Y_gc                                   (eastward, unchanged)
        //
        // psi = phiGc - phiRad  (geocentric - geodetic; negative in N hemisphere)
        //
        // Substituting X_gc = -Btheta, Z_gc = -Br:
        //   X_geo = -Btheta * cos(psi) + (-Br) * sin(psi) = -(Btheta*cos(psi) + Br*sin(psi))
        //   Z_geo = -(-Btheta)*sin(psi) + (-Br)*cos(psi)  = Btheta*sin(psi) - Br*cos(psi)

        val psi    = phiGc - phiRad
        val cosPsi = cos(psi)
        val sinPsi = sin(psi)

        val X = -(Btheta * cosPsi + Br * sinPsi)   // northward geographic (nT)
        val Y = Bphi                                  // eastward geographic  (nT)
        val Z = Btheta * sinPsi - Br * cosPsi        // downward geographic  (nT; positive toward Earth)

        // ── 6. Derived quantities ─────────────────────────────────────────────
        val H    = sqrt(X * X + Y * Y)               // horizontal intensity (nT)
        val decl = Math.toDegrees(atan2(Y, X))        // declination (°, positive east)
        val incl = Math.toDegrees(atan2(Z, H))        // inclination/dip (°, positive down)
        val F    = (sqrt(X * X + Y * Y + Z * Z) / 1000.0).toFloat()  // total field (µT)

        return WmmResult(
            declination = decl.toFloat(),
            inclination = incl.toFloat(),
            totalField  = F
        )
    }

    // ─── Schmidt quasi-normalized Legendre polynomials ────────────────────────

    /**
     * Computes the Schmidt quasi-normalized associated Legendre polynomials P(n,m)(x)
     * where x = cos(theta) = cos(colatitude) = sin(geocentric latitude).
     *
     * These are the standard "semi-normalized" polynomials used in the WMM Gauss
     * coefficient normalization. The recursion follows McLain (1974) and the NOAA
     * WMM Technical Note:
     *
     *   P(0,0) = 1
     *   P(1,0) = x
     *   P(1,1) = y = sin(theta) = cos(geocentric latitude)
     *
     * For n ≥ 2:
     *   Diagonal:     P(n,n)   = y * sqrt((2n-1)/(2n)) * P(n-1,n-1)
     *   Sub-diagonal: P(n,n-1) = x * sqrt(2n-1) * P(n-1,n-1)
     *   General:      P(n,m)   = [x*(2n-1)*P(n-1,m) - sqrt((n-1)²-m²)*P(n-2,m)] / sqrt(n²-m²)
     *
     * @param ct cos(theta) = sin(geocentric latitude)
     * @param st sin(theta) = cos(geocentric latitude), always ≥ 0
     */
    private fun schmidtP(ct: Double, st: Double): Array<DoubleArray> {
        val P = Array(MAX_N + 2) { DoubleArray(MAX_N + 2) }
        P[0][0] = 1.0
        P[1][0] = ct
        P[1][1] = st
        for (n in 2..MAX_N) {
            // Diagonal
            P[n][n] = st * sqrt((2.0 * n - 1.0) / (2.0 * n)) * P[n - 1][n - 1]
            // Sub-diagonal
            P[n][n - 1] = ct * sqrt(2.0 * n - 1.0) * P[n - 1][n - 1]
            // General three-term
            for (m in 0..n - 2) {
                val nf   = n.toDouble()
                val mf   = m.toDouble()
                val denom = sqrt(nf * nf - mf * mf)
                val c1    = (2.0 * nf - 1.0) * ct * P[n - 1][m]
                val c2    = sqrt((nf - 1.0) * (nf - 1.0) - mf * mf) * P[n - 2][m]
                P[n][m] = (c1 - c2) / denom
            }
        }
        return P
    }

    /**
     * Computes the colatitude derivative dP(n,m)/dθ using the standard WMM recursion:
     *
     *   dP(n,m)/dθ = [n * cos(θ) * P(n,m) - sqrt(n²-m²) * P(n-1,m)] / sin(θ)
     *
     * where P(n-1,m) ≡ 0 when n-1 < m (the polynomial is zero for degree < order).
     * At the geographic poles (sin(θ) → 0) the function returns 0 for numerical stability.
     *
     * @param P    the Schmidt polynomial array from [schmidtP]
     * @param n    degree (1..MAX_N)
     * @param m    order (0..n)
     * @param ct   cos(theta) = sin(geocentric lat)
     * @param st   sin(theta) = cos(geocentric lat) ≥ 0
     */
    private fun schmidtdP(P: Array<DoubleArray>, n: Int, m: Int, ct: Double, st: Double): Double {
        if (st < 1e-10) return 0.0   // at geographic poles
        val pPrev  = if (m <= n - 1) P[n - 1][m] else 0.0
        val sqrtNM = sqrt(maxOf(0.0, n.toDouble() * n - m.toDouble() * m))
        return (n * ct * P[n][m] - sqrtNM * pPrev) / st
    }
}

// ─── Supporting types ─────────────────────────────────────────────────────────

/** Thrown when the WMM2025 coefficient file cannot be loaded or parsed. */
class WmmLoadException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Converts the current clock time to a decimal epoch year.
 * Uses the actual number of days in the year for leap-year correctness.
 * Example: 2025-01-01 → 2025.0; 2025-07-02 (day 183 of 365) → ≈ 2025.499.
 */
fun Clock.toEpochYears(): Double {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    cal.timeInMillis = this.nowMs()
    val year       = cal.get(Calendar.YEAR)
    val dayOfYear  = cal.get(Calendar.DAY_OF_YEAR)        // 1-based
    val daysInYear = cal.getActualMaximum(Calendar.DAY_OF_YEAR).toDouble()
    return year + (dayOfYear - 1.0) / daysInYear
}
