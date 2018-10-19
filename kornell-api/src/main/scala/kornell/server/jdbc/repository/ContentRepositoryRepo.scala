package kornell.server.jdbc.repository

import kornell.core.entity.ContentRepository
import kornell.server.jdbc.SQL._

class ContentRepositoryRepo(uuid: String) {

  def get: ContentRepository = ContentRepositoriesRepo.getByRepositoryUUID(uuid).get

  def update(repo: ContentRepository): ContentRepository = {
    sql"""
      update ContentRepository set
      | repositoryType = ${repo.getRepositoryType.toString},
      | accessKeyId = ${repo.getAccessKeyId},
      | secretAccessKey = ${repo.getSecretAccessKey},
      | bucketName = ${repo.getBucketName},
      | region = ${repo.getRegion},
      | prefix = ${repo.getPrefix},
      | path = ${repo.getPath},
      | accountName = ${repo.getAccountName},
      | accountKey = ${repo.getAccountKey},
      | container = ${repo.getContainer}
      | where uuid = ${repo.getUUID}
    """.executeUpdate

    ContentRepositoriesRepo.updateCache(repo)
    repo
  }
}

object ContentRepositoryRepo {
  def apply(uuid: String) = new ContentRepositoryRepo(uuid)
}
