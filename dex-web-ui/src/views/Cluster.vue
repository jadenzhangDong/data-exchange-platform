<template>
  <div>
    <h2>🔧 集群监控</h2>
    <el-card style="margin-bottom:20px;">
      <el-row :gutter="20">
        <el-col :span="6">
          <div style="font-size:14px;color:#909399;">Master 状态</div>
          <div style="font-size:20px;font-weight:500;margin-top:4px;">
            <el-tag :type="masterStatus === 'Active' ? 'success' : 'info'">
              {{ masterStatus }}
            </el-tag>
          </div>
        </el-col>
        <el-col :span="6">
          <div style="font-size:14px;color:#909399;">在线 Worker</div>
          <div style="font-size:20px;font-weight:500;margin-top:4px;color:#409EFF;">
            {{ onlineWorkers }}
          </div>
        </el-col>
        <el-col :span="6">
          <div style="font-size:14px;color:#909399;">总 Worker</div>
          <div style="font-size:20px;font-weight:500;margin-top:4px;">
            {{ workers.length }}
          </div>
        </el-col>
        <el-col :span="6" style="text-align:right;">
          <el-button type="primary" @click="refresh" :loading="refreshing">刷新</el-button>
          <el-button type="warning" @click="handleSwitchMaster" :disabled="!canSwitch">主备切换</el-button>
        </el-col>
      </el-row>
    </el-card>

    <el-card>
      <el-table :data="workers" border v-loading="loading">
        <el-table-column prop="workerId" label="Worker ID" min-width="200" />
        <el-table-column prop="host" label="Host" width="140" />
        <el-table-column prop="port" label="端口" width="80" />
        <el-table-column prop="load" label="负载" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.load < 3 ? 'success' : row.load < 8 ? 'warning' : 'danger'">
              {{ row.load }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="weight" label="权重" width="80" align="center" />
        <el-table-column prop="status" label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="tags" label="标签" min-width="120">
          <template #default="{ row }">
            <el-tag v-for="tag in row.tags" :key="tag" size="small" style="margin:2px;">
              {{ tag }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="320" fixed="right">
          <template #default="{ row }">
            <!-- 只有 ONLINE 才能禁用或排空 -->
            <el-button size="small" type="warning" @click="disableWorker(row.workerId)" v-if="row.status === 'ONLINE'">
              禁用
            </el-button>
            <el-button size="small" type="info" @click="drainWorker(row.workerId)" v-if="row.status === 'ONLINE'">
              排空
            </el-button>
            <!-- 非 ONLINE 显示启用按钮 -->
            <el-button size="small" type="success" @click="enableWorker(row.workerId)" v-if="row.status !== 'ONLINE'">
              启用
            </el-button>
            <el-button size="small" type="primary" @click="editWeight(row)" v-if="row.status === 'ONLINE'">
              权重
            </el-button>
            <!-- 状态标签 -->
            <el-tag v-if="row.status === 'DRAINING'" size="small" type="warning">排空中</el-tag>
            <el-tag v-else-if="row.status === 'DISABLED'" size="small" type="danger">已禁用</el-tag>
            <el-tag v-else-if="row.status === 'OFFLINE'" size="small" type="info">已离线</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 权重编辑对话框 -->
    <el-dialog v-model="weightDialogVisible" title="修改 Worker 权重" width="400px">
      <el-form label-width="80px">
        <el-form-item label="Worker">
          <span>{{ editingWorker?.workerId }}</span>
        </el-form-item>
        <el-form-item label="权重">
          <el-input-number v-model="newWeight" :min="1" :max="100" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="weightDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveWeight">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { getWorkers, getMasterStatus, switchMaster } from '../api'
import { ElMessage } from 'element-plus'
import axios from 'axios'

export default {
  setup() {
    const workers = ref([])
    const masterStatus = ref('--')
    const loading = ref(false)
    const refreshing = ref(false)
    const weightDialogVisible = ref(false)
    const editingWorker = ref(null)
    const newWeight = ref(10)

    const onlineWorkers = computed(() => {
      return workers.value.filter(w => w.status === 'ONLINE' || w.status === 'DRAINING').length
    })

    const canSwitch = computed(() => true) // 根据实际配置

    const getStatusType = (status) => {
      const map = { 'ONLINE': 'success', 'DRAINING': 'warning', 'DISABLED': 'danger', 'OFFLINE': 'info' }
      return map[status] || 'info'
    }

    const refresh = async () => {
      refreshing.value = true
      try {
        const status = await getMasterStatus()
        masterStatus.value = typeof status === 'string' ? status : status.status || '--'
        const list = await getWorkers()
        workers.value = list.map(w => ({
          ...w,
          tags: w.tags || [],
          status: w.status || 'ONLINE',
          weight: w.weight || 10
        }))
      } catch (e) {
        console.error('刷新失败', e)
      } finally {
        refreshing.value = false
      }
    }

    const enableWorker = async (workerId) => {
      try {
        await axios.post(`/api/master/worker/enable/${workerId}`)
        ElMessage.success('Worker 已启用')
        await refresh()
      } catch (e) {
        ElMessage.error('操作失败：' + e.message)
      }
    }

    const disableWorker = async (workerId) => {
      try {
        await axios.post(`/api/master/worker/disable/${workerId}`)
        ElMessage.success('Worker 已禁用')
        await refresh()
      } catch (e) {
        ElMessage.error('操作失败：' + e.message)
      }
    }

    const drainWorker = async (workerId) => {
      try {
        const resp = await axios.post(`/api/master/worker/drain/${workerId}`)
        ElMessage.success(resp.data)
        await refresh()
      } catch (e) {
        ElMessage.error('操作失败：' + e.message)
      }
    }

    const editWeight = (row) => {
      editingWorker.value = row
      newWeight.value = row.weight || 10
      weightDialogVisible.value = true
    }

    const saveWeight = async () => {
      try {
        await axios.post(`/api/master/worker/weight/${editingWorker.value.workerId}?weight=${newWeight.value}`)
        ElMessage.success('权重已更新')
        weightDialogVisible.value = false
        await refresh()
      } catch (e) {
        ElMessage.error('更新失败：' + e.message)
      }
    }

    const handleSwitchMaster = async () => {
      try {
        await switchMaster()
        ElMessage.success('切换请求已发送')
        setTimeout(refresh, 3000)
      } catch (e) {
        ElMessage.error('切换失败：' + e.message)
      }
    }

    onMounted(() => {
      refresh()
      const timer = setInterval(refresh, 30000)
      onUnmounted(() => clearInterval(timer))
    })

    return {
      workers,
      masterStatus,
      loading,
      refreshing,
      onlineWorkers,
      canSwitch,
      weightDialogVisible,
      editingWorker,
      newWeight,
      getStatusType,
      refresh,
      disableWorker,
      enableWorker,
      drainWorker,
      editWeight,
      saveWeight,
      handleSwitchMaster
    }
  }
}
</script>