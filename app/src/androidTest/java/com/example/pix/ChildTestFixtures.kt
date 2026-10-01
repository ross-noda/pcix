package com.example.pix

import com.example.pix.data.*

/** Existing regression fixtures now create full tasks through the normal repository. */
suspend fun TaskRepository.saveChildForTest(task: TaskEntity) {
    val existing=details(task.id)
    if(existing==null) {
        val siblings=task.parentTaskId?.let { details(it)?.visibleChildren }.orEmpty()
        create(task.copy(sortOrder=(siblings.minOfOrNull { it.sortOrder } ?: 0L)-1))
    } else {
        edit(task,existing.tags.map { it.id }.toSet())
        complete(task.id,task.isCompleted)
    }
}
