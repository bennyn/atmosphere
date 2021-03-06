/*
 * Copyright 2012 Jeanfrancois Arcand
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.atmosphere.interceptor;

import org.atmosphere.cpr.Action;
import org.atmosphere.cpr.ApplicationConfig;
import org.atmosphere.cpr.AsyncIOInterceptorAdapter;
import org.atmosphere.cpr.AsyncIOWriter;
import org.atmosphere.cpr.AtmosphereConfig;
import org.atmosphere.cpr.AtmosphereInterceptorAdapter;
import org.atmosphere.cpr.AtmosphereInterceptorWriter;
import org.atmosphere.cpr.AtmosphereRequest;
import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.cpr.AtmosphereResource.TRANSPORT;
import org.atmosphere.cpr.AtmosphereResourceEvent;
import org.atmosphere.cpr.AtmosphereResourceEventListenerAdapter;
import org.atmosphere.cpr.AtmosphereResponse;
import org.atmosphere.cpr.FrameworkConfig;
import org.atmosphere.cpr.HeaderConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;

import static org.atmosphere.cpr.ApplicationConfig.PROPERTY_USE_STREAM;
import static org.atmosphere.cpr.HeaderConfig.X_ATMOSPHERE_TRANSPORT;

/**
 * Padding interceptor for Browser that needs whitespace when streaming is used.
 *
 * @author Jeanfrancois Arcand
 */
public class StreamingAtmosphereInterceptor extends AtmosphereInterceptorAdapter {

    private static final Logger logger = LoggerFactory.getLogger(StreamingAtmosphereInterceptor.class);

    private static final byte[] padding;
    private static final String paddingText;

    static {
        StringBuffer whitespace = new StringBuffer();
        for (int i = 0; i < 4096; i++) {
            whitespace.append(" ");
        }
        whitespace.append("\n");
        paddingText = whitespace.toString();
        padding = paddingText.getBytes();
    }

    @Override
    public void configure(AtmosphereConfig config) {
    }

    private void writePadding(AtmosphereResponse response) {
        AtmosphereRequest request = response.request();
        if (request != null && request.getAttribute("paddingWritten") != null) return;

        request.setAttribute(FrameworkConfig.TRANSPORT_IN_USE, HeaderConfig.STREAMING_TRANSPORT);
        try {
            response.write(padding, true).flushBuffer();
        } catch (IOException e) {
            logger.debug("", e);
        }
    }

    @Override
    public Action inspect(final AtmosphereResource r) {
        final AtmosphereResponse response = r.getResponse();

        if (r.transport().equals(TRANSPORT.STREAMING)) {
            super.inspect(r);

            r.addEventListener(new AtmosphereResourceEventListenerAdapter() {
                @Override
                public void onPreSuspend(AtmosphereResourceEvent event) {
                    writePadding(response);
                }
            });

            AsyncIOWriter writer = response.getAsyncIOWriter();
            if (AtmosphereInterceptorWriter.class.isAssignableFrom(writer.getClass())) {
                AtmosphereInterceptorWriter.class.cast(writer).interceptor(new AsyncIOInterceptorAdapter() {
                    private void padding() {
                        if (!r.isSuspended()) {
                            writePadding(response);
                            r.getRequest().setAttribute("paddingWritten", "true");
                        }
                    }

                    @Override
                    public void prePayload(AtmosphereResponse response, byte[] data, int offset, int length) {
                        padding();
                    }

                    @Override
                    public void postPayload(AtmosphereResponse response, byte[] data, int offset, int length) {
                    }
                });
            } else {
                logger.warn("Unable to apply {}. Your AsyncIOWriter must implement {}", getClass().getName(), AtmosphereInterceptorWriter.class.getName());
            }
        }
        return Action.CONTINUE;
    }

    @Override
    public String toString() {
        return "Streaming Interceptor Support";
    }
}
