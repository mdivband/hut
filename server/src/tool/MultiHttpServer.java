package tool;

import java.io.File;
import java.io.IOException;

public class MultiHttpServer extends HttpServer {


    public MultiHttpServer(int port) {
        super(port);
    }

    /**
     * Serves the content for a request, using the context handler for the
     * requested context.
     *
     * @param req the request
     * @param resp the response into which the content is written
     * @throws IOException if an error occurs
     */
    @Override
    protected void serve(Request req, Response resp) throws IOException {
        // get context handler to handle request
        String path = req.getPath();
        ContextHandler handler = req.getVirtualHost().getContext(path);
        if (handler == null) {
            resp.sendError(404);
            return;
        }
        // serve request
        int status = 404;
        // add directory index if necessary
        if (path.endsWith("/")) {
            String index = req.getVirtualHost().getDirectoryIndex();
            if (index != null) {
                // This is only when the first contact is made, so here we will do redirects
                req.setPath("/1/" + path + index);
                //req.getHeaders().add("Thread", "1");
                status = handler.serve(req, resp);
                req.setPath(path);
            }
        }
        if (status == 404)
            req.setPath("/1/" + path);
            status = handler.serve(req, resp);
        if (status > 0)
            resp.sendError(status);
    }

    /**
     * The {@code FileContextHandler} services a context by mapping it
     * to a file or folder (recursively) on disk.
     */
    public static class FileContextHandler implements ContextHandler {

        protected final File base;
        protected final String context;

        public FileContextHandler(File dir, String context) throws IOException {
            this.base = dir.getCanonicalFile();
            this.context = trimRight(context, '/'); // remove trailing slash;
        }

        public int serve(Request req, Response resp) throws IOException {
            return serveFile(base, context, req, resp);
        }
    }

    /**
     * Serves a context's contents from a file based resource.
     *
     * The file is located by stripping the given context prefix from
     * the request's path, and appending the result to the given base directory.
     *
     * Missing, forbidden and otherwise invalid files return the appropriate
     * error response. Directories are served as an HTML index page if the
     * virtual host allows one, or a forbidden error otherwise. Files are
     * sent with their corresponding content types, and handle conditional
     * and partial retrievals according to the RFC.
     *
     * @param base the base directory to which the context is mapped
     * @param context the context which is mapped to the base directory
     * @param req the request
     * @param resp the response into which the content is written
     * @return the HTTP status code to return, or 0 if a response was sent
     * @throws IOException if an error occurs
     */
    public static int serveFile(File base, String context,
                                Request req, Response resp) throws IOException {
        String relativePath = req.getPath().substring(context.length());
        File file = new File(base, relativePath).getCanonicalFile();
        if (!file.exists() || file.isHidden()) {
            return 404;
        } else if (!file.canRead()
                || !file.getPath().startsWith(base.getPath())) { // validate
            return 403;
        } else if (file.isDirectory()) {
            if (relativePath.endsWith("/") || relativePath.length() == 0) {
                if (!req.getVirtualHost().isAllowGeneratedIndex())
                    return 403;
                resp.send(200, createIndex(file, req.getPath()));
            } else { // redirect to the normalized directory URL ending with '/'
                resp.redirect(req.getBaseURL() + req.getPath() + "/", true);
            }
        } else {
            serveFileContent(file, req, resp);
        }
        return 0;
    }

}
