package com.gentics.mesh.core.image.spi;

public class CacheFileInfo {
	public final String path;
	public final boolean exists;

	public CacheFileInfo(String path, boolean exists) {
		this.path = path;
		this.exists = exists;
	}
}
