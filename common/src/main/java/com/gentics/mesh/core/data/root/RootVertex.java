package com.gentics.mesh.core.data.root;

import static com.gentics.mesh.core.data.relationship.GraphPermission.READ_PERM;
import static com.gentics.mesh.core.rest.error.Errors.error;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;

import java.util.Iterator;
import java.util.List;
import java.util.Stack;

import com.syncleus.ferma.tx.Tx;
import com.gentics.mesh.context.InternalActionContext;
import com.gentics.mesh.core.data.MeshAuthUser;
import com.gentics.mesh.core.data.MeshCoreVertex;
import com.gentics.mesh.core.data.MeshVertex;
import com.gentics.mesh.core.data.page.TransformablePage;
import com.gentics.mesh.core.data.page.impl.DynamicTransformablePageImpl;
import com.gentics.mesh.core.data.relationship.GraphPermission;
import com.gentics.mesh.core.data.search.SearchQueueBatch;
import com.gentics.mesh.core.rest.common.RestModel;
import com.gentics.mesh.graphdb.spi.Database;
import com.gentics.mesh.parameter.PagingParameters;
import com.syncleus.ferma.FramedGraph;
import com.tinkerpop.blueprints.Edge;
import com.tinkerpop.blueprints.Vertex;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

/**
 * A root vertex is an aggregation vertex that is used to aggregate various basic elements such as users, nodes, groups.
 */
public interface RootVertex<T extends MeshCoreVertex<? extends RestModel, T>> extends MeshVertex {

	public static final Logger log = LoggerFactory.getLogger(RootVertex.class);

	Database database();

	/**
	 * Return a list of all elements. Only use this method if you know that the root->item relation only yields a specific kind of item.
	 * 
	 * @deprecated Use {@link #findAllIt()} instead.
	 * @return
	 */
	@Deprecated
	default public List<? extends T> findAll() {
		return out(getRootLabel()).toListExplicit(getPersistanceClass());
	}

	/**
	 * Return an iterator of all elements. Only use this method if you know that the root->item relation only yields a specific kind of item.
	 * 
	 * @return
	 */
	default public Iterable<? extends T> findAllIt() {
		return out(getRootLabel()).frameExplicit(getPersistanceClass());
	}

	/**
	 * Return an iterator of all elements and use the stored type information to load the items. The {@link #findAllIt()} will use explicit typing and thus will
	 * be faster. Only use that method if you know that your relation only yields a specific kind of item.
	 * 
	 * @return
	 */
	default public Iterable<? extends T> findAllDynamic() {
		return out(getRootLabel()).frame(getPersistanceClass());
	}

	/**
	 * Find the visible elements and return a paged result.
	 * 
	 * @param ac
	 *            action context
	 * @param pagingInfo
	 *            Paging information object that contains page options.
	 * 
	 * @return
	 */
	default public TransformablePage<? extends T> findAll(InternalActionContext ac, PagingParameters pagingInfo) {
		return new DynamicTransformablePageImpl<>(ac.getUser(), this, pagingInfo, READ_PERM, null, true);
	}

	/**
	 * Find all elements and return a paged result. No permission check will be performed.
	 * 
	 * @param ac
	 *            action context
	 * @param pagingInfo
	 *            Paging information object that contains page options.
	 * 
	 * @return
	 */
	default public TransformablePage<? extends T> findAllNoPerm(InternalActionContext ac, PagingParameters pagingInfo) {
		return new DynamicTransformablePageImpl<>(ac.getUser(), this, pagingInfo, null, null, true);
	}

	/**
	 * Find the element with the given name.
	 * 
	 * @param name
	 * @return
	 */
	default public T findByName(String name) {
		return out(getRootLabel()).has("name", name).nextOrDefaultExplicit(getPersistanceClass(), null);
	}

	/**
	 * Load the object by name and check the given permission.
	 * 
	 * @param ac
	 *            Context to be used in order to check user permissions
	 * @param name
	 *            Name of the object that should be loaded
	 * @param perm
	 *            Permission that must be granted in order to load the object
	 * 
	 * @return
	 */
	default public T findByName(InternalActionContext ac, String name, GraphPermission perm) {
		T element = findByName(name);
		if (element == null) {
			throw error(NOT_FOUND, "object_not_found_for_name", name);
		}

		MeshAuthUser requestUser = ac.getUser();
		String elementUuid = element.getUuid();
		if (requestUser.hasPermission(element, perm)) {
			return element;
		} else {
			throw error(FORBIDDEN, "error_missing_perm", elementUuid);
		}
	}

	/**
	 * Find the element with the given uuid.
	 * 
	 * @param uuid
	 *            Uuid of the element to be located
	 * @return Found element or null if the element could not be located
	 */
	default public T findByUuid(String uuid) {
		FramedGraph graph = Tx.getActive().getGraph();
		// 1. Find the element with given uuid within the whole graph
		Iterator<Vertex> it = database().getVertices(getPersistanceClass(), new String[] { "uuid" }, new String[] { uuid });
		if (it.hasNext()) {
			Vertex potentialElement = it.next();
			// 2. Use the edge index to determine whether the element is part of this root vertex
			Iterable<Edge> edges = graph.getEdges("e." + getRootLabel().toLowerCase() + "_inout",
					database().createComposedIndexKey(potentialElement.getId(), getId()));
			if (edges.iterator().hasNext()) {
				return graph.frameElementExplicit(potentialElement, getPersistanceClass());
			}
		}
		return null;
	}

	/**
	 * Load the object by uuid and check the given permission.
	 * 
	 * @param ac
	 *            Context to be used in order to check user permissions
	 * @param uuid
	 *            Uuid of the object that should be loaded
	 * @param perm
	 *            Permission that must be granted in order to load the object
	 * @return Loaded element. A not found error will be thrown if the element could not be found. Returned value will never be null.
	 */
	default public T loadObjectByUuid(InternalActionContext ac, String uuid, GraphPermission perm) {
		return loadObjectByUuid(ac, uuid, perm, true);
	}

	/**
	 * Load the object by uuid and check the given permission.
	 * 
	 * @param ac
	 *            Context to be used in order to check user permissions
	 * @param uuid
	 *            Uuid of the object that should be loaded
	 * @param perm
	 *            Permission that must be granted in order to load the object
	 * @param errorIfNotFound
	 *            True if an error should be thrown, when the element could not be found
	 * @return Loaded element. If errorIfNotFound is true, a not found error will be thrown if the element could not be found and the returned value will never
	 *         be null.
	 */
	default public T loadObjectByUuid(InternalActionContext ac, String uuid, GraphPermission perm, boolean errorIfNotFound) {
		T element = findByUuid(uuid);
		if (element == null) {
			if (errorIfNotFound) {
				throw error(NOT_FOUND, "object_not_found_for_uuid", uuid);
			} else {
				return null;
			}
		}

		MeshAuthUser requestUser = ac.getUser();
		String elementUuid = element.getUuid();
		if (requestUser.hasPermission(element, perm)) {
			return element;
		} else {
			throw error(FORBIDDEN, "error_missing_perm", elementUuid);
		}
	}

	/**
	 * Load the object by uuid. No permission check will be performed.
	 * 
	 * @param uuid
	 *            Uuid of the object that should be loaded
	 * @param errorIfNotFound
	 *            True if an error should be thrown, when the element could not be found
	 * @return Loaded element. If errorIfNotFound is true, a not found error will be thrown if the element could not be found and the returned value will never
	 *         be null.
	 */
	default public T loadObjectByUuidNoPerm(String uuid, boolean errorIfNotFound) {
		T element = findByUuid(uuid);
		if (element == null) {
			if (errorIfNotFound) {
				throw error(NOT_FOUND, "object_not_found_for_uuid", uuid);
			} else {
				return null;
			}
		}
		return element;
	}

	/**
	 * Resolve the given stack to the vertex.
	 * 
	 * @param stack
	 *            Stack which contains the remaining path elements which should be resolved starting with the current graph element
	 * @return
	 */
	default public MeshVertex resolveToElement(Stack<String> stack) {
		if (log.isDebugEnabled()) {
			log.debug("Resolving for {" + getPersistanceClass().getSimpleName() + "}.");
			if (stack.isEmpty()) {
				log.debug("Stack: is empty");
			} else {
				log.debug("Stack: " + stack.peek());
			}
		}
		if (stack.isEmpty()) {
			return this;
		} else {
			String uuid = stack.pop();
			if (stack.isEmpty()) {
				return findByUuid(uuid);
			} else {
				throw error(BAD_REQUEST, "Can't resolve remaining segments. Next segment would be: " + stack.peek());
			}
		}
	}

	/**
	 * Create a new object which is connected or directly related to this aggregation vertex.
	 * 
	 * @param ac
	 *            Context which is used to load information needed for the object creation
	 * @param batch
	 */
	default T create(InternalActionContext ac, SearchQueueBatch batch) {
		return create(ac, batch, null);
	}

	/**
	 * Create a new object which is connected or directly related to this aggregation vertex.
	 * 
	 * @param ac
	 *            Context which is used to load information needed for the object creation
	 * @param batch
	 * @param uuid
	 *            optional uuid to create the object with a given uuid (null to create a random uuid)
	 */
	T create(InternalActionContext ac, SearchQueueBatch batch, String uuid);

	/**
	 * Add the given item to the this root vertex.
	 * 
	 * @param item
	 */
	default public void addItem(T item) {
		FramedGraph graph = getGraph();
		Iterable<Edge> edges = graph.getEdges("e." + getRootLabel().toLowerCase() + "_inout",
				database().createComposedIndexKey(item.getId(), getId()));
		if (!edges.iterator().hasNext()) {
			linkOut(item, getRootLabel());
		}
	}

	/**
	 * Remove the given item from this root vertex.
	 * 
	 * @param item
	 */
	default public void removeItem(T item) {
		unlinkOut(item, getRootLabel());
	}

	/**
	 * Return the label for the item edges.
	 * 
	 * @return
	 */
	String getRootLabel();

	/**
	 * Return the ferma graph persistance class for the items of the root vertex. (eg. NodeImpl, TagImpl...)
	 * 
	 * @return
	 */
	Class<? extends T> getPersistanceClass();

}
