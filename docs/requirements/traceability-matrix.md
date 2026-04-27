# Requirements Traceability Matrix

| User Story | US Title | Requirements | FSPEC |
|------------|----------|-------------|-------|
| US-01 | Taking a bearing on a distant object | REQ-SENSOR-04, REQ-DISPLAY-07, REQ-DISPLAY-08, REQ-CAPTURE-01, REQ-CAPTURE-02 | TBD |
| US-02 | Orienting a building's 坐向 | REQ-SENSOR-01–04, REQ-DISPLAY-04, REQ-DISPLAY-05, REQ-DISPLAY-06, REQ-NORTH-01–04, REQ-DECL-01 | TBD |
| US-03 | Calibrating in the field | REQ-CAL-01, REQ-CAL-02, REQ-CAL-03, REQ-CAL-04, REQ-CAL-05, REQ-SENSOR-01 | FSPEC-CAL-01, FSPEC-CAL-02, FSPEC-CAL-03, FSPEC-NAV-01 |
| US-04 | Detecting magnetic interference | REQ-DETECT-01, REQ-DETECT-02, REQ-DETECT-03, REQ-DETECT-04, REQ-CAL-03, REQ-DISPLAY-03 | FSPEC-HIST-05 |
| US-05 | Recording a bearing | REQ-CAPTURE-01, REQ-CAPTURE-02, REQ-CAPTURE-04, REQ-CAPTURE-06, REQ-DETECT-05 | TBD |
| US-06 | Reviewing saved bearings | REQ-CAPTURE-03, REQ-CAPTURE-04, REQ-CAPTURE-05 | FSPEC-HIST-01, FSPEC-HIST-02, FSPEC-HIST-03, FSPEC-HIST-04, FSPEC-HIST-05, FSPEC-NAV-01 |
| US-07 | 24 Mountains / 分金 reading | REQ-DISPLAY-04, REQ-DISPLAY-05, REQ-L10N-02, REQ-LUOPAN-01, REQ-LUOPAN-02 | TBD |
| US-08 | General consumer quick check | REQ-DISPLAY-01, REQ-DISPLAY-02, REQ-DISPLAY-03, REQ-DISPLAY-11, REQ-L10N-01 | TBD |
| US-09 | Checking declination | REQ-NORTH-01, REQ-NORTH-02, REQ-NORTH-03, REQ-NORTH-04, REQ-DECL-01, REQ-DECL-02 | TBD |
| US-10 | Operating without GPS | REQ-NORTH-03 | TBD |
| US-11 | Holding device vertically | REQ-SENSOR-02, REQ-DISPLAY-02 | TBD |
| US-12 | 後天八卦 方位 and 十二地支 direction reading | REQ-LUOPAN-01, REQ-LUOPAN-02, REQ-DISPLAY-05 | TBD |

## Requirement → User Story Reverse Index

| Requirement | Title | User Stories | FSPEC |
|-------------|-------|-------------|-------|
| REQ-SENSOR-01 | Uncalibrated magnetometer | US-02, US-03 | — |
| REQ-SENSOR-02 | Accelerometer / gravity | US-02, US-11 | — |
| REQ-SENSOR-03 | Gyroscope | US-01, US-02 | — |
| REQ-SENSOR-04 | 9-DOF sensor fusion | US-01, US-02, US-08 | — |
| REQ-SENSOR-05 | Sampling rate / power | NFR | — |
| REQ-SENSOR-06 | Noise variance tracking | US-04, US-07 | — |
| REQ-SENSOR-07 | Sensor capability logging | US-03 (indirect) | FSPEC-SENSOR-01 |
| REQ-CAL-01 | Calibration data collection | US-03 | — |
| REQ-CAL-02 | Ellipsoid fitting | US-03 | — |
| REQ-CAL-03 | Calibration quality scoring | US-02, US-04, US-07 | — |
| REQ-CAL-04 | Calibration persistence | US-03 | — |
| REQ-CAL-05 | Recalibration prompts | US-03, US-04 | FSPEC-CAL-01, FSPEC-CAL-02, FSPEC-CAL-03, FSPEC-NAV-01 |
| REQ-CAL-06 | Manual calibration bypass | US-09 (indirect) | — |
| REQ-DETECT-01 | Field magnitude check | US-04, US-07 | — |
| REQ-DETECT-02 | Inclination check | US-04 | — |
| REQ-DETECT-03 | Interference warning UX | US-04 | — |
| REQ-DETECT-04 | Noise spike detection | US-02, US-07 | — |
| REQ-DETECT-05 | Interference flag in bearing record | US-05 | FSPEC-HIST-05 |
| REQ-NORTH-01 | Bundled WMM2025 | US-09, US-10 | — |
| REQ-NORTH-02 | Android GeomagneticField fallback | US-09, US-10 | — |
| REQ-NORTH-03 | GPS handling for declination | US-10 | — |
| REQ-NORTH-04 | North label on all headings | US-09, US-02 | — |
| REQ-DISPLAY-01 | Mode switching | US-01, US-02, US-08 | — |
| REQ-DISPLAY-02 | Modern Mode compass rose | US-08, US-11 | — |
| REQ-DISPLAY-03 | Confidence indicator | US-04, US-08 | — |
| REQ-DISPLAY-04 | Luopan Mode overview | US-02, US-07 | — |
| REQ-DISPLAY-05 | Luopan numerical readout | US-02, US-07, US-12 | — |
| REQ-DISPLAY-06 | 坐向 lock | US-02 | — |
| REQ-DISPLAY-07 | Sighting mode camera | US-01, US-11 | — |
| REQ-DISPLAY-08 | Sighting mode capture | US-01 | — |
| REQ-DISPLAY-09 | Heading smoothing control | US-09 (indirect) | — |
| REQ-DISPLAY-10 | Screen-on wake lock | US-02, US-09 | — |
| REQ-DISPLAY-11 | Accessibility text scaling | US-08 | — |
| REQ-DISPLAY-12 | Dark / light theme | US-07 | — |
| REQ-CAPTURE-01 | Bearing record schema | US-05, US-09 | — |
| REQ-CAPTURE-02 | Capture flow UX | US-05 | — |
| REQ-CAPTURE-03 | Bearing history screen | US-06 | FSPEC-HIST-01, FSPEC-HIST-02, FSPEC-HIST-03, FSPEC-HIST-04, FSPEC-HIST-05, FSPEC-NAV-01 |
| REQ-CAPTURE-04 | Data persistence | US-06 | — |
| REQ-CAPTURE-05 | Share / export | US-06, US-09 | — |
| REQ-CAPTURE-06 | Location privacy confirmation | US-05 | — |
| REQ-DECL-01 | North type toggle | US-09, US-02 | — |
| REQ-DECL-02 | Declination info panel | US-09 | — |
| REQ-DECL-03 | Grid north | US-09 | — |
| REQ-L10N-01 | Four supported locales | All | — |
| REQ-L10N-02 | Luopan terminology handling | US-07, US-08 | — |
| REQ-LUOPAN-01 | 後天八卦 ring — trigram + 方位 direction labels | US-02, US-07, US-12 | — |
| REQ-LUOPAN-02 | 十二地支 ring — twelve directional sectors | US-02, US-07, US-12 | — |
