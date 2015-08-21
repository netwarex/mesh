package com.gentics.mesh.core.data.root.impl;

import static com.gentics.mesh.core.data.relationship.GraphPermission.CREATE_PERM;
import static com.gentics.mesh.core.data.relationship.GraphRelationships.HAS_PROJECT;
import static com.gentics.mesh.json.JsonUtil.fromJson;
import static com.gentics.mesh.util.VerticleHelper.getUser;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.CONFLICT;

import java.util.Stack;

import org.apache.commons.lang.NotImplementedException;
import org.apache.commons.lang3.StringUtils;

import com.gentics.mesh.cli.BootstrapInitializer;
import com.gentics.mesh.core.data.MeshAuthUser;
import com.gentics.mesh.core.data.MeshVertex;
import com.gentics.mesh.core.data.Project;
import com.gentics.mesh.core.data.User;
import com.gentics.mesh.core.data.impl.ProjectImpl;
import com.gentics.mesh.core.data.root.MeshRoot;
import com.gentics.mesh.core.data.root.MicroschemaContainerRoot;
import com.gentics.mesh.core.data.root.NodeRoot;
import com.gentics.mesh.core.data.root.ProjectRoot;
import com.gentics.mesh.core.data.root.SchemaContainerRoot;
import com.gentics.mesh.core.data.root.TagFamilyRoot;
import com.gentics.mesh.core.data.root.TagRoot;
import com.gentics.mesh.core.data.service.I18NService;
import com.gentics.mesh.core.rest.error.HttpStatusCodeErrorException;
import com.gentics.mesh.core.rest.project.ProjectCreateRequest;
import com.gentics.mesh.error.InvalidPermissionException;
import com.gentics.mesh.etc.MeshSpringConfiguration;
import com.gentics.mesh.etc.RouterStorage;
import com.gentics.mesh.graphdb.Trx;
import com.gentics.mesh.graphdb.spi.Database;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;

public class ProjectRootImpl extends AbstractRootVertex<Project>implements ProjectRoot {

	private static final Logger log = LoggerFactory.getLogger(ProjectRootImpl.class);

	@Override
	protected Class<? extends Project> getPersistanceClass() {
		return ProjectImpl.class;
	}

	@Override
	protected String getRootLabel() {
		return HAS_PROJECT;
	}

	@Override
	public void addProject(Project project) {
		addItem(project);
	}

	@Override
	public void removeProject(Project project) {
		removeItem(project);
	}

	// TODO unique

	@Override
	public Project create(String name, User creator) {
		Project project = getGraph().addFramedVertex(ProjectImpl.class);
		project.setName(name);
		project.getNodeRoot();
		project.createBaseNode(creator);

		project.setCreator(creator);
		project.setCreationTimestamp(System.currentTimeMillis());
		project.setEditor(creator);
		project.setLastEditedTimestamp(System.currentTimeMillis());

		project.getTagRoot();
		project.getSchemaContainerRoot();
		project.getTagFamilyRoot();

		addItem(project);

		return project;
	}

	@Override
	public void resolveToElement(Stack<String> stack, Handler<AsyncResult<? extends MeshVertex>> resultHandler) {
		if (stack.isEmpty()) {
			resultHandler.handle(Future.succeededFuture(this));
		} else {
			String uuidSegment = stack.pop();
			findByUuid(uuidSegment, rh -> {
				if (rh.succeeded()) {
					Project project = rh.result();
					if (stack.isEmpty()) {
						resultHandler.handle(Future.succeededFuture(project));
					} else {
						String nestedRootNode = stack.pop();
						switch (nestedRootNode) {
						case TagFamilyRoot.TYPE:
							TagFamilyRoot tagFamilyRoot = project.getTagFamilyRoot();
							tagFamilyRoot.resolveToElement(stack, resultHandler);
							break;
						case SchemaContainerRoot.TYPE:
							SchemaContainerRoot schemaRoot = project.getSchemaContainerRoot();
							schemaRoot.resolveToElement(stack, resultHandler);
							break;
						case MicroschemaContainerRoot.TYPE:
							// MicroschemaContainerRoot microschemaRoot = project.get
							// project.getMicroschemaRoot();
							throw new NotImplementedException();
							// break;
						case NodeRoot.TYPE:
							NodeRoot nodeRoot = project.getNodeRoot();
							nodeRoot.resolveToElement(stack, resultHandler);
							break;
						case TagRoot.TYPE:
							TagRoot tagRoot = project.getTagRoot();
							tagRoot.resolveToElement(stack, resultHandler);
							return;
						default:
							resultHandler.handle(Future.failedFuture("Unknown project element {" + nestedRootNode + "}"));
							return;
						}
					}
				} else {
					resultHandler.handle(Future.failedFuture(rh.cause()));
					return;
				}
			});
		}
	}

	@Override
	public void delete() {
		throw new NotImplementedException("The project root should never be deleted.");
	}

	@Override
	public void create(RoutingContext rc, Handler<AsyncResult<Project>> handler) {
		Database db = MeshSpringConfiguration.getMeshSpringConfiguration().database();
		RouterStorage routerStorage = RouterStorage.getRouterStorage();
		I18NService i18n = I18NService.getI18n();
		MeshRoot meshRoot = BootstrapInitializer.getBoot().meshRoot();
		BootstrapInitializer boot = BootstrapInitializer.getBoot();

		// TODO also create a default object schema for the project. Move this into service class
		// ObjectSchema defaultContentSchema = objectSchemaRoot.findByName(, name)
		ProjectCreateRequest requestModel = fromJson(rc, ProjectCreateRequest.class);
		MeshAuthUser requestUser = getUser(rc);

		if (StringUtils.isEmpty(requestModel.getName())) {
			rc.fail(new HttpStatusCodeErrorException(BAD_REQUEST, i18n.get(rc, "project_missing_name")));
			return;
		}
		try (Trx tx = new Trx(db)) {
			if (requestUser.hasPermission(boot.projectRoot(), CREATE_PERM)) {
				if (boot.projectRoot().findByName(requestModel.getName()) != null) {
					rc.fail(new HttpStatusCodeErrorException(CONFLICT, i18n.get(rc, "project_conflicting_name")));
				} else {
					try (Trx txCreate = new Trx(db)) {
						Project project = create(requestModel.getName(), requestUser);
						project.setCreator(requestUser);
						try {
							routerStorage.addProjectRouter(project.getName());
							if (log.isInfoEnabled()) {
								log.info("Registered project {" + project.getName() + "}");
							}
							requestUser.addCRUDPermissionOnRole(meshRoot, CREATE_PERM, project);
							requestUser.addCRUDPermissionOnRole(meshRoot, CREATE_PERM, project.getBaseNode());
							requestUser.addCRUDPermissionOnRole(meshRoot, CREATE_PERM, project.getTagFamilyRoot());
							requestUser.addCRUDPermissionOnRole(meshRoot, CREATE_PERM, project.getTagRoot());
							requestUser.addCRUDPermissionOnRole(meshRoot, CREATE_PERM, project.getNodeRoot());
							txCreate.commit();
							handler.handle(Future.succeededFuture(project));
						} catch (Exception e) {
							// TODO should we really fail here?
							tx.rollback();
							rc.fail(new HttpStatusCodeErrorException(BAD_REQUEST, i18n.get(rc, "Error while adding project to router storage"), e));
						}
					}

				}
			} else {
				rc.fail(new InvalidPermissionException(i18n.get(rc, "error_missing_perm", boot.projectRoot().getUuid())));
			}
		}

	}
}
