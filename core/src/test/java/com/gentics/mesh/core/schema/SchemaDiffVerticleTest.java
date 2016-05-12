package com.gentics.mesh.core.schema;

import static com.gentics.mesh.assertj.MeshAssertions.assertThat;
import static com.gentics.mesh.core.rest.schema.change.impl.SchemaChangeModel.DISPLAY_FIELD_NAME_KEY;
import static com.gentics.mesh.core.rest.schema.change.impl.SchemaChangeOperation.ADDFIELD;
import static com.gentics.mesh.core.rest.schema.change.impl.SchemaChangeOperation.REMOVEFIELD;
import static com.gentics.mesh.core.rest.schema.change.impl.SchemaChangeOperation.UPDATESCHEMA;
import static com.gentics.mesh.util.MeshAssert.assertSuccess;
import static com.gentics.mesh.util.MeshAssert.latchFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.gentics.mesh.core.AbstractSpringVerticle;
import com.gentics.mesh.core.data.schema.SchemaContainer;
import com.gentics.mesh.core.rest.error.GenericRestException;
import com.gentics.mesh.core.rest.schema.BinaryFieldSchema;
import com.gentics.mesh.core.rest.schema.HtmlFieldSchema;
import com.gentics.mesh.core.rest.schema.Schema;
import com.gentics.mesh.core.rest.schema.StringFieldSchema;
import com.gentics.mesh.core.rest.schema.change.impl.SchemaChangesListModel;
import com.gentics.mesh.core.rest.schema.impl.HtmlFieldSchemaImpl;
import com.gentics.mesh.core.rest.schema.impl.SchemaModel;
import com.gentics.mesh.core.rest.schema.impl.StringFieldSchemaImpl;
import com.gentics.mesh.core.verticle.schema.SchemaVerticle;
import com.gentics.mesh.test.AbstractRestVerticleTest;
import com.gentics.mesh.util.FieldUtil;

import io.vertx.core.Future;

public class SchemaDiffVerticleTest extends AbstractRestVerticleTest {

	@Autowired
	private SchemaVerticle verticle;

	@Override
	public List<AbstractSpringVerticle> getAdditionalVertices() {
		List<AbstractSpringVerticle> list = new ArrayList<>();
		list.add(verticle);
		return list;
	}

	private Schema getSchema() {
		Schema request = new SchemaModel();
		request.setName("content");
		request.setDisplayField("title");
		request.setSegmentField("filename");

		StringFieldSchema nameFieldSchema = new StringFieldSchemaImpl();
		nameFieldSchema.setName("name");
		nameFieldSchema.setLabel("Name");
		nameFieldSchema.setRequired(true);
		request.addField(nameFieldSchema);

		StringFieldSchema filenameFieldSchema = new StringFieldSchemaImpl();
		filenameFieldSchema.setName("filename");
		filenameFieldSchema.setLabel("Filename");
		request.addField(filenameFieldSchema);

		StringFieldSchema titleFieldSchema = new StringFieldSchemaImpl();
		titleFieldSchema.setName("title");
		titleFieldSchema.setLabel("Title");
		request.addField(titleFieldSchema);

		HtmlFieldSchema contentFieldSchema = new HtmlFieldSchemaImpl();
		contentFieldSchema.setName("content");
		contentFieldSchema.setLabel("Content");
		request.addField(contentFieldSchema);

		request.setContainer(false);
		return request;
	}

	@Test
	public void testDiffDisplayField() throws GenericRestException, Exception {
		SchemaContainer container = schemaContainer("content");
		Schema request = getSchema();
		request.setDisplayField("name");

		Future<SchemaChangesListModel> future = getClient().diffSchema(container.getUuid(), request);
		latchFor(future);
		assertSuccess(future);
		SchemaChangesListModel changes = future.result();
		assertNotNull(changes);
		// We expect one change that indicates that the displayField property has changed.
		assertThat(changes.getChanges()).hasSize(1);
		assertThat(changes.getChanges().get(0)).is(UPDATESCHEMA).hasProperty(DISPLAY_FIELD_NAME_KEY, "name");

	}

	@Test
	public void testNoDiff() {
		SchemaContainer schema = schemaContainer("content");
		Schema request = getSchema();
		Future<SchemaChangesListModel> future = getClient().diffSchema(schema.getUuid(), request);
		latchFor(future);
		assertSuccess(future);
		SchemaChangesListModel changes = future.result();
		assertNotNull(changes);
		assertThat(changes.getChanges()).isEmpty();
	}

	@Test
	public void testAddField() {
		SchemaContainer schema = schemaContainer("content");
		Schema request = getSchema();
		BinaryFieldSchema binaryField = FieldUtil.createBinaryFieldSchema("binary");
		binaryField.setAllowedMimeTypes("one", "two");
		request.addField(binaryField);
		Future<SchemaChangesListModel> future = getClient().diffSchema(schema.getUuid(), request);
		latchFor(future);
		assertSuccess(future);
		SchemaChangesListModel changes = future.result();
		assertNotNull(changes);
		assertThat(changes.getChanges()).hasSize(2);
		assertThat(changes.getChanges().get(0)).is(ADDFIELD).forField("binary");
		assertThat(changes.getChanges().get(1)).is(UPDATESCHEMA).hasProperty("order",
				new String[] { "name", "filename", "title", "content", "binary" });
	}

	@Test
	public void testRemoveField() {
		SchemaContainer schema = schemaContainer("content");
		Schema request = getSchema();
		request.removeField("content");
		Future<SchemaChangesListModel> future = getClient().diffSchema(schema.getUuid(), request);
		latchFor(future);
		assertSuccess(future);
		SchemaChangesListModel changes = future.result();
		assertNotNull(changes);
		assertThat(changes.getChanges()).hasSize(2);
		assertThat(changes.getChanges().get(0)).is(REMOVEFIELD).forField("content");
		assertThat(changes.getChanges().get(1)).is(UPDATESCHEMA).hasProperty("order", new String[] { "name", "filename", "title" });
		assertNotNull("A default migration script should have been added to the change.", changes.getChanges().get(0).getMigrationScript());
	}

}
