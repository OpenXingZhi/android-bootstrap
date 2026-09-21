package com.xingzhi.bootstrap

import java.net.URI

/**
 * Compiled-in OpenPGP trust root for detached XingZhi configuration signatures.
 *
 * Not loaded from assets or the config origin: a packaged `.gpg` swap must
 * not replace the verifier. [uri] may refresh the published certificate for
 * the same primary key. Rotating to a new primary key requires an application
 * update.
 */
object TrustedPublicKey {
    val uri: URI = URI("https://gitee.com/chuxubank.gpg")

    private val ARMORED = """
        -----BEGIN PGP PUBLIC KEY BLOCK-----

        mDMEZnP01RYJKwYBBAHaRw8BAQdAKbnXT18ba5cCt9WA3hGQYfSSOFSpaPIURGAJ
        vNENJLe0JkplcnJ5IENodSAoWGluZ1poaSkgPGNodXh1YmFua0BxcS5jb20+iJME
        ExYKADsWIQQJuzCdaMU3ZIk6iUKp3CamqTAH8QUCZxmYMAIbAwULCQgHAgIiAgYV
        CgkICwIEFgIDAQIeBwIXgAAKCRCp3CamqTAH8fgfAP9SrL7zZPsyKGjfJBKCPUvi
        JqZreolMk1l9uCBQfvXljwEAo9hBd2wkzrM0cc+WX9YkuKu4xpflmIrdeNPWQI9p
        fQ24OARmc/TVEgorBgEEAZdVAQUBAQdAPg3A3L8n8iTh20Gced7rA4N0CiTPzW9G
        0pWHC/0Pv0EDAQgHiHgEGBYKACAWIQQJuzCdaMU3ZIk6iUKp3CamqTAH8QUCZnP0
        1QIbDAAKCRCp3CamqTAH8bjyAP4+70LJfXDN5baQMg4ocvBo+ocZlyp3tRoxIVJU
        rvRFeAEA8Yi2wNGGVZG7UDpNbxcUVucoQn4ZOqxRRZYXoDPliw0=
        =w623
        -----END PGP PUBLIC KEY BLOCK-----
        """.trimIndent()

    val bytes: ByteArray =
        ARMORED.replace("\n", "\r\n").toByteArray(Charsets.US_ASCII)
}
