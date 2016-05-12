package com.gentics.mesh.test.definition;

import com.gentics.mesh.core.rest.error.GenericRestException;

public interface CrudVerticleTestCases {

	// Create
	void testCreate() throws Exception;

	void testCreateReadDelete() throws Exception;

	// Read
	void testReadByUUID() throws Exception;
	
	void testReadByUuidWithRolePerms();

	void testReadByUUIDWithMissingPermission() throws Exception;

	void testReadMultiple() throws Exception;

	// Update
	void testUpdate() throws Exception;

	void testUpdateByUUIDWithoutPerm() throws Exception;

	void testUpdateWithBogusUuid() throws GenericRestException, Exception;

	// Delete
	void testDeleteByUUID() throws Exception;

	void testDeleteByUUIDWithNoPermission() throws Exception;

}
