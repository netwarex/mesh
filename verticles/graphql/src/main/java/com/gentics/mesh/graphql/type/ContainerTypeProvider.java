package com.gentics.mesh.graphql.type;

import static com.gentics.mesh.core.data.relationship.GraphPermission.READ_PERM;
import static com.gentics.mesh.core.data.relationship.GraphPermission.READ_PUBLISHED_PERM;
import static graphql.Scalars.GraphQLBoolean;
import static graphql.Scalars.GraphQLString;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;
import static graphql.schema.GraphQLObjectType.newObject;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.gentics.mesh.core.data.EditorTrackingVertex;
import com.gentics.mesh.core.data.NodeGraphFieldContainer;
import com.gentics.mesh.core.data.Project;
import com.gentics.mesh.core.data.User;
import com.gentics.mesh.core.data.node.Node;
import com.gentics.mesh.graphql.context.GraphQLContext;

import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLObjectType.Builder;
import graphql.schema.GraphQLTypeReference;

@Singleton
public class ContainerTypeProvider extends AbstractTypeProvider {

	@Inject
	public NodeFieldTypeProvider nodeFieldTypeProvider;

	@Inject
	public ContainerTypeProvider() {
	}

	public Object parentNodeFetcher(DataFetchingEnvironment env) {
		GraphQLContext gc = env.getContext();
		NodeGraphFieldContainer container = env.getSource();
		Node node = container.getParentNode();
		return gc.requiresPerm(node, READ_PERM, READ_PUBLISHED_PERM);
	}

	public Object editorFetcher(DataFetchingEnvironment env) {
		GraphQLContext gc = env.getContext();
		EditorTrackingVertex vertex = env.getSource();
		User user = vertex.getEditor();
		return gc.requiresPerm(user, READ_PERM);
	}

	public GraphQLObjectType getContainerType(Project project) {

		Builder type = newObject().name("Container")
				.description("Language specific node container which contains the node fields");

		// .node
		type.field(newFieldDefinition().name("node")
				.description("Node to which the container belongs")
				.type(new GraphQLTypeReference("Node"))
				.dataFetcher(this::parentNodeFetcher));

		type.field(newFieldDefinition().name("edited")
				.description("ISO8601 formatted edit timestamp")
				.type(GraphQLString)
				.dataFetcher(env -> {
					EditorTrackingVertex vertex = env.getSource();
					return vertex.getLastEditedDate();
				}));

		//		type.field(newFieldDefinition().name("created")
		//				.description("ISO8601 formatted created date string")
		//				.type(GraphQLString)
		//				.dataFetcher(fetcher -> {
		//					Object source = fetcher.getSource();
		//					if (source instanceof CreatorTrackingVertex) {
		//						return ((CreatorTrackingVertex) source).getCreationDate();
		//					}
		//					return null;
		//				})
		//				.build());
		//
		//		type.field(newFieldDefinition().name("creator")
		//				.description("Creator of the element")
		//				.type(new GraphQLTypeReference("User"))
		//				.dataFetcher(fetcher -> {
		//					Object source = fetcher.getSource();
		//					if (source instanceof CreatorTrackingVertex) {
		//						return ((CreatorTrackingVertex) source).getCreator();
		//					}
		//					return null;
		//				})
		//				.build());

		// .editor
		type.field(newFieldDefinition().name("editor")
				.description("Editor of the element")
				.type(new GraphQLTypeReference("User"))
				.dataFetcher(this::editorFetcher));

		// .isPublished
		type.field(newFieldDefinition().name("isPublished")
				.description("Check whether the container is published")
				.type(GraphQLBoolean)
				.dataFetcher(env -> {
					GraphQLContext gc = env.getContext();
					NodeGraphFieldContainer container = env.getSource();
					return container.isPublished(gc.getRelease()
							.getUuid());
				}));

		// .isDraft
		type.field(newFieldDefinition().name("isDraft")
				.description("Check whether the container is a draft")
				.type(GraphQLBoolean)
				.dataFetcher(env -> {
					GraphQLContext gc = env.getContext();
					NodeGraphFieldContainer container = env.getSource();
					return container.isDraft(gc.getRelease()
							.getUuid());
				}));

		// .version
		type.field(newFieldDefinition().name("version")
				.description("Version of the container")
				.type(GraphQLString)
				.dataFetcher(env -> {
					NodeGraphFieldContainer container = env.getSource();
					return container.getVersion()
							.getFullVersion();
				}));

		// .fields
		type.field(newFieldDefinition().name("fields")
				.type(nodeFieldTypeProvider.getSchemaFieldsType(project))
				.dataFetcher(env -> {
					// The fields can be accessed via the container so we can directly pass it along.
					NodeGraphFieldContainer container = env.getSource();
					return container;
				}));

		// .language
		type.field(newFieldDefinition().name("language")
				.type(GraphQLString)
				.dataFetcher(env -> {
					NodeGraphFieldContainer container = env.getSource();
					return container.getLanguage()
							.getLanguageTag();
				}));

		return type.build();
	}
}