package kornell.core.entity;

public interface ContentRepository {
    public static String TYPE = EntityFactory.PREFIX + "contentRepo+json";

    //Common fields
    String getUUID();
    void setUUID(String UUID);

    RepositoryType getRepositoryType();
    void setRepositoryType(RepositoryType repositoryType);

    String getPrefix();
    void setPrefix(String prefix);

    String getInstitutionUUID();
    void setInstitutionUUID(String institutionUUID);

    //S3
    String getAccessKeyId();
    void setAccessKeyId(String accessKeyId);

    String getSecretAccessKey();
    void setSecretAccessKey(String secretAccessKey);

    String getBucketName();
    void setBucketName(String bucketName);

    String getRegion();
    void setRegion(String region);

    //Azure
    String getAccountName();
    void setAccountName(String accountName);

    String getAccountKey();
    void setAccountKey(String accountKey);

    String getContainer();
    void setContainer(String container);

    //FS
    String getPath();
    void setPath(String path);
}
