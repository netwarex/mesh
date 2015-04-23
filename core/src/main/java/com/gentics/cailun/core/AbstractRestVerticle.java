package com.gentics.cailun.core;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.apex.Route;
import io.vertx.ext.apex.Router;
import io.vertx.ext.apex.RoutingContext;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.http.entity.ContentType;
import org.neo4j.graphdb.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gentics.cailun.core.data.model.auth.CaiLunPermission;
import com.gentics.cailun.core.data.model.auth.PermissionType;
import com.gentics.cailun.core.data.model.generic.AbstractPersistable;
import com.gentics.cailun.error.EntityNotFoundException;
import com.gentics.cailun.error.HttpStatusCodeErrorException;
import com.gentics.cailun.error.InvalidPermissionException;
import com.gentics.cailun.etc.config.CaiLunConfigurationException;
import com.gentics.cailun.paging.PagingInfo;

public abstract class AbstractRestVerticle extends AbstractSpringVerticle {

	private static final Logger log = LoggerFactory.getLogger(AbstractRestVerticle.class);

	public static final String APPLICATION_JSON = ContentType.APPLICATION_JSON.getMimeType();
	private static final String DEPTH_PARAM_KEY = "depth";

	public static final int DEFAULT_PER_PAGE = 25;

	protected Router localRouter = null;
	protected String basePath;
	protected HttpServer server;

	protected AbstractRestVerticle(String basePath) {
		this.basePath = basePath;
	}

	@Override
	public void start() throws Exception {
		this.localRouter = setupLocalRouter();
		if (localRouter == null) {
			throw new CaiLunConfigurationException("The local router was not setup correctly. Startup failed.");
		}

		log.info("Starting http server..");
		server = vertx.createHttpServer(new HttpServerOptions().setPort(config().getInteger("port")));
		server.requestHandler(routerStorage.getRootRouter()::accept);
		server.listen();
		log.info("Started http server.. Port: " + config().getInteger("port"));
		registerEndPoints();

	}

	public abstract void registerEndPoints() throws Exception;

	public abstract Router setupLocalRouter();

	@Override
	public void stop() throws Exception {
		localRouter.clear();
	}

	public Router getRouter() {
		return localRouter;
	}

	public HttpServer getServer() {
		return server;
	}

	/**
	 * Wrapper for getRouter().route(path)
	 * 
	 * @return
	 */
	protected Route route(String path) {
		Route route = localRouter.route(path);
		return route;
	}

	/**
	 * Wrapper for getRouter().route()
	 * 
	 * @return
	 */
	protected Route route() {
		Route route = localRouter.route();
		return route;
	}

	public <T extends AbstractPersistable> void loadObjectByUuid(RoutingContext rc, String uuid, PermissionType permType,
			Handler<AsyncResult<T>> resultHandler) {
		if (StringUtils.isEmpty(uuid)) {
			// TODO i18n, add info about uuid source?
			throw new HttpStatusCodeErrorException(400, "missing uuid");
		}
		vertx.executeBlocking((Future<T> fut) -> {
			T node = (T) genericNodeService.findByUUID(uuid);
			if (node == null) {
				fut.fail(new EntityNotFoundException(i18n.get(rc, "object_not_found_for_uuid", uuid)));
				return;
			}
			rc.session().hasPermission(new CaiLunPermission(node, permType).toString(), handler -> {
				if (!handler.result()) {
					fut.fail(new InvalidPermissionException(i18n.get(rc, "error_missing_perm", node.getUuid())));
					return;
				} else {
					fut.complete(node);
					return;
				}
			});
		}, res -> {
			if (res.failed()) {
				rc.fail(res.cause());
			} else {
				try (Transaction tx = graphDb.beginTx()) {
					resultHandler.handle(res);
					tx.success();
				}
			}
		});

	}

	public <T extends AbstractPersistable> void loadObject(RoutingContext rc, String uuidParamName, PermissionType permType,
			Handler<AsyncResult<T>> resultHandler) {

		String uuid = rc.request().params().get(uuidParamName);
		if (StringUtils.isEmpty(uuid)) {
			rc.fail(new HttpStatusCodeErrorException(400, i18n.get(rc, "error_request_parameter_missing", uuidParamName)));
			return;
		}

		loadObjectByUuid(rc, uuid, permType, resultHandler);
	}

	/**
	 * Check the permission and throw an invalid permission exception when no matching permission could be found.
	 *
	 * @param rc
	 * @param node
	 * @param type
	 * @return
	 */
	protected void hasPermission(RoutingContext rc, AbstractPersistable node, PermissionType type, Handler<AsyncResult<Boolean>> resultHandler)
			throws InvalidPermissionException {
		rc.session().hasPermission(new CaiLunPermission(node, type).toString(), handler -> {
			if (!handler.result()) {
				rc.fail(new InvalidPermissionException(i18n.get(rc, "error_missing_perm", node.getUuid())));
				return;
			} else {
				try (Transaction tx = graphDb.beginTx()) {
					resultHandler.handle(Future.succeededFuture(handler.result()));
					tx.success();
				}
			}
		});
	}

	public Map<String, String> splitQuery(String query) throws UnsupportedEncodingException {
		Map<String, String> queryPairs = new LinkedHashMap<String, String>();
		if (query == null) {
			return queryPairs;
		}
		String[] pairs = query.split("&");
		for (String pair : pairs) {
			int idx = pair.indexOf("=");
			queryPairs.put(URLDecoder.decode(pair.substring(0, idx), "UTF-8"), URLDecoder.decode(pair.substring(idx + 1), "UTF-8"));
		}
		return queryPairs;
	}

	/**
	 * Extract the paging information from the request parameters. The paging information contains information about the number of the page that is currently
	 * requested and the amount of items that should be included in a single page.
	 * 
	 * @param rc
	 * @return Paging information
	 */
	protected PagingInfo getPagingInfo(RoutingContext rc) {
		MultiMap params = rc.request().params();
		int page = NumberUtils.toInt(params.get("page"), 1);
		int perPage = NumberUtils.toInt(params.get("per_page"), DEFAULT_PER_PAGE);
		if (page < 1) {
			throw new HttpStatusCodeErrorException(400, i18n.get(rc, "error_invalid_paging_parameters"));
		}
		if (perPage <= 0) {
			throw new HttpStatusCodeErrorException(400, i18n.get(rc, "error_invalid_paging_parameters"));
		}
		return new PagingInfo(page, perPage);
	}

	protected int getDepth(RoutingContext rc) {
		String query = rc.request().query();
		Map<String, String> queryPairs;
		try {
			queryPairs = splitQuery(query);
		} catch (UnsupportedEncodingException e) {
			log.error("Could not decode query string.", e);
			return 0;
		}
		String value = queryPairs.get(DEPTH_PARAM_KEY);
		return NumberUtils.toInt(value, 0);
	}

}
