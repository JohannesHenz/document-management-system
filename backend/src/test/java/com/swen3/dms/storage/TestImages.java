package com.swen3.dms.storage;

import org.testcontainers.utility.DockerImageName;

/** Container images used by the tests; kept in line with docker-compose.yml. */
public final class TestImages {

    /** MinIO no longer publishes community images; this is a community-maintained build of the same server. */
    public static final DockerImageName MINIO = DockerImageName.parse("pgsty/minio:RELEASE.2026-08-04T00-00-00Z")
            .asCompatibleSubstituteFor("minio/minio");

    private TestImages() {
    }
}
