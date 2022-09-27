/*
 * Copyright (c) 2021-2022 - Yupiik SAS - https://www.yupiik.com
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.yupiik.maven.extension;

import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.execution.MavenSession;
import org.codehaus.plexus.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.util.Optional;

import static java.util.Optional.ofNullable;

@Component(role = AbstractMavenLifecycleParticipant.class, hint = "yupiik-proxy-configurer")
public class ProxyConfigurer extends AbstractMavenLifecycleParticipant {
    private final Logger logger;

    public ProxyConfigurer() {
        System.setProperty("jdk.http.auth.tunneling.disabledSchemes", System.getProperty("jdk.http.auth.tunneling.disabledSchemes", ""));
        logger = LoggerFactory.getLogger(getClass());
    }

    @Override
    public void afterSessionStart(final MavenSession session) {
        synchronized (Authenticator.class) {
            if (Authenticator.getDefault() != null) {
                logger.info("Authenticator not null so skipping init.");
                return;
            }
            Authenticator.setDefault(new AutoAuthenticator(session));
            logger.info("Set auto-Authenticator.");
        }
    }

    @Override
    public void afterSessionEnd(final MavenSession session) {
        synchronized (Authenticator.class) {
            if (Authenticator.getDefault() instanceof AutoAuthenticator) {
                Authenticator.setDefault(null);
                logger.info("Removing auto-Authenticator");
            }
        }
    }

    private static class AutoAuthenticator extends Authenticator {
        private final MavenSession session;

        private AutoAuthenticator(final MavenSession session) {
            this.session = session;
        }

        @Override
        protected PasswordAuthentication getPasswordAuthentication() {
            if (getRequestorType() != RequestorType.PROXY) {
                return super.getPasswordAuthentication();
            }
            return createPasswordAuthenticator();
        }

        private PasswordAuthentication createPasswordAuthenticator() {
            final var username = conf(getRequestingProtocol() + ".proxyUser");
            final var password = conf(getRequestingProtocol() + ".proxyPassword");
            return username
                    .flatMap(user -> password.map(pwd -> new PasswordAuthentication(user, pwd.toCharArray())))
                    .orElseGet(super::getPasswordAuthentication);
        }

        private Optional<String> conf(final String key) {
            return ofNullable(session.getSystemProperties().getProperty(key))
                    .or(() -> ofNullable(session.getUserProperties().getProperty(key)));
        }
    }
}
