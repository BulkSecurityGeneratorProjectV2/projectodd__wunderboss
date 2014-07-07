package org.projectodd.wunderboss.messaging.hornetq;

import org.projectodd.wunderboss.Options;
import org.projectodd.wunderboss.codecs.Codecs;
import org.projectodd.wunderboss.messaging.Destination;
import org.projectodd.wunderboss.messaging.Listener;
import org.projectodd.wunderboss.messaging.Message;
import org.projectodd.wunderboss.messaging.MessageHandler;
import org.projectodd.wunderboss.messaging.Queue;
import org.projectodd.wunderboss.messaging.Reply;
import org.projectodd.wunderboss.messaging.Session;

import java.util.HashMap;
import java.util.Map;

public class ResponseRouter implements Listener, MessageHandler {

    public ResponseRouter(String id) {
        this.id = id;
    }

    @Override
    public Reply onMessage(Message msg, Session session) throws Exception {
        String id = ((HornetQMessage)msg).requestID();
        HornetQResponse response = responses.remove(id);
        if (response == null) {
            throw new IllegalStateException("No responder for id " + id);
        }

        response.deliver(msg);

        return null;
    }


    public void registerResponse(String id, HornetQResponse response) {
        this.responses.put(id, response);
    }


    public synchronized static ResponseRouter routerFor(Queue queue, Codecs codecs,
                                                        Options<Destination.ListenOption> options) {
        ResponseRouter router = routers.get(queue.name());
        if (router == null) {
            router = new ResponseRouter(queue.name());
            try {
                router.setEnclosingListener(queue.listen(router, codecs, options));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            routers.put(router.id(), router);
        }

        return router;
    }

    public synchronized static void closeRouterFor(Queue queue) throws Exception {
        ResponseRouter router = routers.get(queue.name());
        if (router != null) {
            router.close();
        }
    }

    @Override
    public void close() throws Exception {
        routers.remove(id());

        if (this.enclosingListener != null) {
            this.enclosingListener.close();
        }
    }

    public String id() {
        return id;
    }

    protected void setEnclosingListener(Listener l) {
        this.enclosingListener = l;
    }

    private final static Map<String, ResponseRouter> routers = new HashMap<>();
    private final Map<String, HornetQResponse> responses = new HashMap<>();
    private final String id;
    private Listener enclosingListener;


}
