package com.example.domain

/**
 * The face mesh rings [Geometry] measures inside — eyes, brows, lips, oval, irises.
 *
 * Ported from `www/face/topology.js`'s `RINGS` table. LANDMARK INDICES ARE NOT REMEMBERED, THEY ARE
 * THE LIBRARY'S OWN — every index below was read off FaceLandmarker's exported constants
 * (FACE_LANDMARKS_LEFT_EYE, _RIGHT_EYE, _LEFT_IRIS, _RIGHT_IRIS, _FACE_OVAL, _LIPS, _LEFT_EYEBROW,
 * _RIGHT_EYEBROW), not copied from a blog post or re-derived by eye. Hardcoding a face-mesh index
 * from memory is exactly how a region ends up measuring an eyebrow for months without anyone
 * noticing, which is the whole reason this table exists as one place rather than as inline numbers
 * scattered through [Geometry].
 */
data class Ring(val vertices: List<Int>, val hull: Boolean)

object Topology {
    val RINGS: Map<String, Ring> = mapOf(
        "faceOval" to Ring(
            listOf(
                10, 338, 297, 332, 284, 251, 389, 356, 454, 323, 361, 288, 397, 365, 379, 378, 400,
                377, 152, 148, 176, 149, 150, 136, 172, 58, 132, 93, 234, 127, 162, 21, 54, 103, 67, 109,
            ),
            hull = false,
        ),
        "leftEye" to Ring(
            listOf(249, 263, 466, 388, 387, 386, 385, 384, 398, 362, 382, 381, 380, 374, 373, 390),
            hull = false,
        ),
        "rightEye" to Ring(
            listOf(7, 33, 246, 161, 160, 159, 158, 157, 173, 133, 155, 154, 153, 145, 144, 163),
            hull = false,
        ),
        "leftBrow" to Ring(listOf(276, 282, 283, 285, 293, 295, 296, 300, 334, 336), hull = true),
        "rightBrow" to Ring(listOf(46, 52, 53, 55, 63, 65, 66, 70, 105, 107), hull = true),
        "lips" to Ring(
            listOf(
                0, 13, 14, 17, 37, 39, 40, 61, 78, 80, 81, 82, 84, 87, 88, 91, 95, 146, 178, 181, 185,
                191, 267, 269, 270, 291, 308, 310, 311, 312, 314, 317, 318, 321, 324, 375, 402, 405,
                409, 415,
            ),
            hull = true,
        ),
        "leftIris" to Ring(listOf(474, 475, 476, 477), hull = false),
        "rightIris" to Ring(listOf(469, 470, 471, 472), hull = false),
    )
}
