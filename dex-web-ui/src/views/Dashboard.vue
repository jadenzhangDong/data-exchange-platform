<template>
  <div>
    <h2 style="margin-bottom:16px;">📈 总览</h2>
    <div class="stat-grid">
      <div class="stat-item"><div class="number">{{ workerCount }}</div><div class="label">在线 Worker</div></div>
      <div class="stat-item"><div class="number">{{ datasourceCount }}</div><div class="label">数据源</div></div>
      <div class="stat-item"><div class="number">{{ taskCount }}</div><div class="label">任务总数</div></div>
      <div class="stat-item"><div class="number">{{ masterStatus }}</div><div class="label">Master 状态</div></div>
    </div>
    <el-card>
      <template #header>
        <span>🚀 快速操作</span>
      </template>
      <el-button type="primary" @click="$router.push('/datasource')">管理数据源</el-button>
      <el-button type="success" @click="$router.push('/tasks')">查看任务</el-button>
      <el-button type="warning" @click="$router.push('/tasks')">新建任务</el-button>
    </el-card>
  </div>
</template>

<script>
import { ref, onMounted } from 'vue'
import { getDatasources, getTaskList, getWorkers, getMasterStatus } from '../api'

export default {
  setup() {
    const workerCount = ref(0)
    const datasourceCount = ref(0)
    const taskCount = ref(0)
    const masterStatus = ref('--')

    const refresh = async () => {
      try {
        const workers = await getWorkers()
        workerCount.value = workers.length
        const ds = await getDatasources()
        datasourceCount.value = ds.length
        const tasks = await getTaskList()
        taskCount.value = tasks.length
        const status = await getMasterStatus()
        masterStatus.value = status
      } catch(e) { console.error(e) }
    }

    onMounted(refresh)

    return { workerCount, datasourceCount, taskCount, masterStatus }
  }
}
</script>

<style scoped>
.stat-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 16px; margin-bottom: 20px; }
.stat-item { background: #fff; border-radius: 8px; padding: 16px; text-align: center; box-shadow: 0 2px 8px rgba(0,0,0,0.04); }
.stat-item .number { font-size: 28px; font-weight: 600; color: #409EFF; }
.stat-item .label { font-size: 14px; color: #909399; margin-top: 4px; }
</style>