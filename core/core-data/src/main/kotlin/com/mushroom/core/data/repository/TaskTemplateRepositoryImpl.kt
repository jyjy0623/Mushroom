package com.mushroom.core.data.repository

import com.mushroom.core.data.db.dao.TaskTemplateDao
import com.mushroom.core.data.mapper.TaskTemplateMapper
import com.mushroom.core.domain.entity.TaskTemplate
import com.mushroom.core.domain.entity.TaskTemplateType
import com.mushroom.core.domain.repository.TaskTemplateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskTemplateRepositoryImpl @Inject constructor(
    private val templateDao: TaskTemplateDao
) : TaskTemplateRepository {

    override fun getAllTemplates(): Flow<List<TaskTemplate>> =
        templateDao.getAllTemplates().map { list -> list.map(TaskTemplateMapper::toDomain) }

    override suspend fun getTemplateByType(type: TaskTemplateType): TaskTemplate? =
        templateDao.getTemplateByType(type.name)?.let(TaskTemplateMapper::toDomain)

    override suspend fun insertTemplate(template: TaskTemplate): Long =
        templateDao.insert(TaskTemplateMapper.toDb(template))

    override suspend fun updateTemplate(template: TaskTemplate) =
        templateDao.update(TaskTemplateMapper.toDb(template))

    override suspend fun deleteTemplate(id: Long) =
        templateDao.deleteById(id)
}
