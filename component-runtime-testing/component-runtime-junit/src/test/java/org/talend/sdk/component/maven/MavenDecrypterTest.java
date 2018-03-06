/**
 * Copyright (C) 2006-2018 Talend Inc. - www.talend.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.talend.sdk.component.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;

import org.junit.Test;

class MavenDecrypterTest {

    @Test
    void encrypted() {
        final ClassLoader loader = MavenDecrypterTest.class.getClassLoader();
        final File settings = new File(loader.getResource("maven/settings.xml").getFile());
        final File settingsSecurity = new File(loader.getResource("maven/settings-security.xml").getFile());
        MavenDecrypter decrypter = new MavenDecrypter(settings, settingsSecurity);

        final Server encrypted = decrypter.find("encrypted");
        assertEquals("repouser", encrypted.getUsername());
        assertEquals("encrypted", encrypted.getPassword());
    }

    @Test
    void clear() {
        final ClassLoader loader = MavenDecrypterTest.class.getClassLoader();
        final File settings = new File(loader.getResource("maven/settings.xml").getFile());
        final File settingsSecurity = new File(loader.getResource("maven/settings-security.xml").getFile());
        MavenDecrypter decrypter = new MavenDecrypter(settings, settingsSecurity);

        final Server encrypted = decrypter.find("clear");
        assertEquals("repouser", encrypted.getUsername());
        assertEquals("repopwd", encrypted.getPassword());
    }

    @Test()
    void noMasterPassword() {
        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            final ClassLoader loader = MavenDecrypterTest.class.getClassLoader();
            final File settings = new File(loader.getResource("maven/settings.xml").getFile());
            final File settingsSecurity = new File(loader.getResource("maven/settings-security-null.xml").getFile());
            MavenDecrypter decrypter = new MavenDecrypter(settings, settingsSecurity);
            decrypter.find("encrypted");
        });

        assertEquals("Master password can't be null or empty.", ex.getMessage());
    }

    @Test
    void emptyMasterPassword() {
        final IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            final ClassLoader loader = MavenDecrypterTest.class.getClassLoader();
            final File settings = new File(loader.getResource("maven/settings-empty.xml").getFile());
            final File settingsSecurity = new File(loader.getResource("maven/settings-security-empty.xml").getFile());
            MavenDecrypter decrypter = new MavenDecrypter(settings, settingsSecurity);
            decrypter.find("encrypted-empty-master");
        });

        assertEquals("Master password can't be null or empty.", ex.getMessage());
    }

}
