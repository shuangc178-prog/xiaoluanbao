package com.example.campuslife.data.plan

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dailyPlanDataStore: DataStore<Preferences> by preferencesDataStore(name = "daily_plan")

/**
 * 每日计划存档。
 *
 * 与打卡记录分开一个 DataStore 文件：同一个 Preferences 文件不能建两个 DataStore 实例，
 * 而这两块数据的读写节奏与生命周期本来也不同。
 */
class DailyPlanStore(private val context: Context) {

    val plans: Flow<List<DailyPlan>> = context.dailyPlanDataStore.data.map { preferences ->
        val plansWithTasks = groupPlans(
            preferences[PLANS_KEY].orEmpty().mapNotNull(::decodePlanTask),
        )
        val daysWithTasks = plansWithTasks.mapTo(mutableSetOf()) { it.epochDay }
        val emptyPlans = preferences[EMPTY_PLAN_DAYS_KEY]
            .orEmpty()
            .mapNotNull(String::toLongOrNull)
            .filterNot { it in daysWithTasks }
            .map { epochDay -> DailyPlan(epochDay = epochDay, tasks = emptyList()) }

        (plansWithTasks + emptyPlans).sortedBy { it.epochDay }
    }

    /** 整天覆盖：同一天再次保存时替换该天的全部条目。 */
    suspend fun savePlan(plan: DailyPlan) {
        context.dailyPlanDataStore.edit { preferences ->
            val kept = preferences[PLANS_KEY]
                .orEmpty()
                .filterNot { encodedEpochDay(it) == plan.epochDay }
            val encoded = plan.tasks.mapIndexed { index, task ->
                encodePlanTask(epochDay = plan.epochDay, index = index, task = task)
            }
            preferences[PLANS_KEY] = (kept + encoded).toSet()

            val emptyDays = preferences[EMPTY_PLAN_DAYS_KEY].orEmpty()
            preferences[EMPTY_PLAN_DAYS_KEY] = if (plan.tasks.isEmpty()) {
                emptyDays + plan.epochDay.toString()
            } else {
                emptyDays - plan.epochDay.toString()
            }
        }
    }

    suspend fun snapshot(): List<DailyPlan> = plans.first()

    /**
     * 恢复时一次替换全部日期，避免逐日 savePlan 让界面经历一串半恢复状态。
     */
    suspend fun replaceAll(plans: List<DailyPlan>) {
        context.dailyPlanDataStore.edit { preferences ->
            preferences[PLANS_KEY] = plans.flatMap { plan ->
                plan.tasks.mapIndexed { index, task ->
                    encodePlanTask(epochDay = plan.epochDay, index = index, task = task)
                }
            }.toSet()
            preferences[EMPTY_PLAN_DAYS_KEY] = plans
                .filter { it.tasks.isEmpty() }
                .map { it.epochDay.toString() }
                .toSet()
        }
    }
}

private val PLANS_KEY = stringSetPreferencesKey("daily_plans")
private val EMPTY_PLAN_DAYS_KEY = stringSetPreferencesKey("empty_plan_days")
