/*
 * Copyright 2014-2015 Red Hat, Inc, and individual contributors.
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

package org.projectodd.wunderboss.web.async.websocket;

import org.projectodd.wunderboss.web.async.Util;

public abstract class WebsocketChannelSkeleton implements WebsocketChannel {


    public WebsocketChannelSkeleton(final OnOpen onOpen,
                                    final OnError onError,
                                    final OnClose onClose,
                                    final OnMessage onMessage) {
        this.onOpen = onOpen;
        this.onClose = onClose;
        this.onMessage = onMessage;
        this.onError = onError;
    }

    @Override
    public Object originatingRequest() {
        return this.originatingRequest;
    }

    @Override
    public void setOriginatingRequest(Object request) {
        this.originatingRequest = request;
    }

    @Override
    public void notifyOpen(final Object context) {
        if (!this.openNotified &&
                this.onOpen != null) {
            this.onOpen.handle(this, context);
        }
        this.openNotified = true;
    }

    @Override
    public void notifyError(Throwable error) {
        if (this.onError != null) {
            this.onError.handle(this, error);
        }
    }

    protected void notifyClose(int code, String reason) {
        if (!closeNotified &&
                this.onClose != null) {
            this.onClose.handle(this, code, reason);
        }
        closeNotified = true;
    }

    protected void notifyMessage(Object message) {
        if (this.onMessage != null) {
            this.onMessage.handle(this, message);
        }
    }

    protected void notifyComplete(OnComplete callback, Throwable error) {
        Util.notifyComplete(this, callback, error);
    }

    private final OnOpen onOpen;
    private final OnClose onClose;
    private final OnMessage onMessage;
    private final OnError onError;
    private Object originatingRequest;
    private boolean closeNotified = false;
    private boolean openNotified = false;
}
