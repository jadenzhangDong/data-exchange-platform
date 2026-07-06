<template>
  <div>
    <h2 style="margin-bottom:16px;">🔍 核对管理</h2>

    <el-tabs v-model="activeTab" type="border-card">
      <!-- ==================== 核对配置 ==================== -->
      <el-tab-pane label="核对配置" name="config">
        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:16px;">
          <span style="color:#909399;font-size:13px;">配置核对任务，支持定时执行</span>
          <el-button type="primary" @click="openConfigDialog()">+ 新增</el-button>
        </div>
        <el-table :data="configs" border v-loading="loading">
          <el-table-column prop="name" label="名称" min-width="120" />
          <el-table-column prop="sourceTable" label="源表" width="120" />
          <el-table-column prop="targetTable" label="目标表" width="120" />
          <el-table-column prop="cronExpression" label="Cron" width="130" />
          <el-table-column prop="enabled" label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="row.enabled ? 'success' : 'danger'">{{ row.enabled ? '启用' : '禁用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="320">
            <template #default="{ row }">
              <el-button size="small" type="primary" @click="openConfigDialog(row)">编辑</el-button>
              <el-button size="small" type="warning" @click="runNow(row.id)">执行</el-button>
              <el-button size="small" @click="toggleEnabled(row)" :disabled="row.enabled === undefined">
                {{ row.enabled ? '禁用' : '启用' }}
              </el-button>
              <el-button size="small" type="danger" @click="deleteConfig(row.id)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- ==================== 执行记录 ==================== -->
      <el-tab-pane label="执行记录" name="jobs">
        <div style="display:flex;align-items:center;gap:12px;margin-bottom:16px;flex-wrap:wrap;">
          <el-select v-model="filterConfigId" placeholder="选择核对配置" @change="loadJobs" style="width:200px;" clearable>
            <el-option v-for="cfg in configs" :key="cfg.id" :label="cfg.name" :value="cfg.id" />
          </el-select>
          <el-button type="primary" @click="loadJobs">刷新</el-button>
          <span style="color:#909399;font-size:13px;">共 {{ jobs.length }} 条记录</span>
        </div>
        <el-table :data="jobs" border v-loading="jobLoading">
          <el-table-column prop="jobId" label="Job ID" width="200" />
          <el-table-column prop="windowStart" label="窗口开始" width="170" />
          <el-table-column prop="windowEnd" label="窗口结束" width="170" />
          <el-table-column prop="sourceCount" label="源记录数" width="100" align="center" />
          <el-table-column prop="targetCount" label="目标记录数" width="100" align="center" />
          <el-table-column prop="sourceMissingCount" label="缺失数" width="90" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.sourceMissingCount > 0" type="danger" size="small">{{ row.sourceMissingCount }}</el-tag>
              <span v-else>{{ row.sourceMissingCount }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="diffCount" label="差异总数" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="row.diffCount > 0 ? 'warning' : 'success'" size="small">{{ row.diffCount }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="getStatusType(row.status)">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="160">
            <template #default="{ row }">
              <el-button size="small" @click="viewDiff(row.jobId)" :disabled="row.diffCount === 0">查看差异</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- ==================== 差异明细 ==================== -->
      <el-tab-pane label="差异明细" name="diff">
        <div style="display:flex;align-items:center;gap:12px;margin-bottom:16px;flex-wrap:wrap;">
          <el-select v-model="diffFilterConfigId" placeholder="选择核对配置" @change="loadDiffsByConfig" style="width:200px;" clearable>
            <el-option v-for="cfg in configs" :key="cfg.id" :label="cfg.name" :value="cfg.id" />
          </el-select>
          <el-select v-model="diffStatusFilter" placeholder="状态筛选" @change="loadDiffsByConfig" style="width:140px;" clearable>
            <el-option label="待处理" value="PENDING" />
            <el-option label="已修复" value="FIXED" />
            <el-option label="已忽略" value="IGNORED" />
          </el-select>
          <el-button type="primary" @click="loadDiffsByConfig">刷新</el-button>
          <span style="color:#909399;font-size:13px;">共 {{ diffs.length }} 条差异</span>
        </div>
        <el-table :data="diffs" border v-loading="diffLoading">
          <el-table-column prop="diffType" label="类型" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="row.diffType === 'MISSING' ? 'danger' : row.diffType === 'EXTRA' ? 'warning' : 'info'">
                {{ row.diffType === 'MISSING' ? '目标缺失' : row.diffType === 'EXTRA' ? '目标多余' : '内容不一致' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="pkValue" label="主键值" min-width="150" />
          <el-table-column prop="status" label="状态" width="120" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 'PENDING' ? 'danger' : row.status === 'FIXED' ? 'success' : 'info'">
                {{ row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="发现时间" width="170" />
          <el-table-column label="操作" width="280">
            <template #default="{ row }">
              <el-button size="small" type="success" @click="fixDiff(row.id)" v-if="row.status === 'PENDING'">
                标记已修复
              </el-button>
              <el-button size="small" type="primary" @click="compensateSingle(row)" v-if="row.status === 'PENDING' && row.diffType === 'MISSING'">
                补数
              </el-button>
              <span v-else style="color:#909399;">已处理</span>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- ===== 配置对话框（简化版） ===== -->
    <el-dialog v-model="configDialogVisible" :title="isEditConfig ? '编辑核对配置' : '新增核对配置'" width="700px">
      <el-form :model="configForm" label-width="100px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="名称">
              <el-input v-model="configForm.name" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="描述">
              <el-input v-model="configForm.description" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="源数据源">
              <el-select v-model="configForm.sourceDataSourceId" style="width:100%;">
                <el-option v-for="ds in dataSources" :key="ds.id" :label="ds.name" :value="ds.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="源表">
              <el-input v-model="configForm.sourceTable" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="目标数据源">
              <el-select v-model="configForm.targetDataSourceId" style="width:100%;">
                <el-option v-for="ds in dataSources" :key="ds.id" :label="ds.name" :value="ds.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="目标表">
              <el-input v-model="configForm.targetTable" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="主键字段">
              <el-input v-model="configForm.primaryKey" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="增量字段">
              <el-input v-model="configForm.incrementColumn" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="窗口单位">
              <el-select v-model="configForm.windowUnit" style="width:100%;">
                <el-option label="小时" value="HOUR" />
                <el-option label="天" value="DAY" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="窗口大小">
              <el-input-number v-model="configForm.windowSize" :min="1" style="width:100%;" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="Cron 表达式">
          <el-input v-model="configForm.cronExpression" placeholder="0 0 * * * ?" />
        </el-form-item>
        <el-form-item label="差异告警阈值">
          <el-input-number v-model="configForm.diffThreshold" :min="1" style="width:200px;" />
          <span style="font-size:12px;color:#909399;">超过此数量只保存前 N 条</span>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="configForm.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="configDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveConfig">保存</el-button>
      </template>
    </el-dialog>

    <!-- ===== 补偿对话框 ===== -->
    <el-dialog v-model="compensateDialogVisible" title="数据补偿" width="500px">
      <p>将对以下主键执行补数：</p>
      <el-input type="textarea" v-model="compensatePkList" :rows="5" placeholder="每行一个主键" />
      <template #footer>
        <el-button @click="compensateDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="doCompensate">执行补偿</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import { ref, onMounted, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import axios from 'axios'

const API_BASE = '/api/reconciliation'

export default {
  setup() {
    // ===== 状态 =====
    const activeTab = ref('config')
    const loading = ref(false)
    const jobLoading = ref(false)
    const diffLoading = ref(false)

    // 配置列表
    const configs = ref([])
    const configDialogVisible = ref(false)
    const isEditConfig = ref(false)
    const configForm = reactive({
      id: null,
      name: '',
      description: '',
      sourceDataSourceId: '',
      targetDataSourceId: '',
      sourceTable: '',
      targetTable: '',
      primaryKey: 'id',
      incrementColumn: 'update_time',
      windowUnit: 'HOUR',
      windowSize: 1,
      cronExpression: '0 0 * * * ?',
      diffThreshold: 1000,
      enabled: true
    })

    // 执行记录
    const jobs = ref([])
    const filterConfigId = ref('')

    // 差异明细
    const diffs = ref([])
    const diffFilterConfigId = ref('')
    const diffStatusFilter = ref('')

    // 数据源列表（用于下拉选择）
    const dataSources = ref([])

    // 补偿
    const compensateDialogVisible = ref(false)
    const compensatePkList = ref('')
    const compensateConfigId = ref('')

    // ===== 方法 =====

    // 加载数据源
    const loadDataSources = async () => {
      try {
        const res = await axios.get('/api/meta/datasource')
        dataSources.value = res.data || []
      } catch (e) {
        console.warn('加载数据源失败', e)
      }
    }

    // 加载核对配置
    const loadConfigs = async () => {
      loading.value = true
      try {
        const res = await axios.get(API_BASE + '/config')
        configs.value = res.data || []
      } finally {
        loading.value = false
      }
    }

    // 打开配置对话框
    const openConfigDialog = (row) => {
      if (row) {
        isEditConfig.value = true
        Object.assign(configForm, row)
      } else {
        isEditConfig.value = false
        Object.assign(configForm, {
          id: null,
          name: '',
          description: '',
          sourceDataSourceId: '',
          targetDataSourceId: '',
          sourceTable: '',
          targetTable: '',
          primaryKey: 'id',
          incrementColumn: 'update_time',
          windowUnit: 'HOUR',
          windowSize: 1,
          cronExpression: '0 0 * * * ?',
          diffThreshold: 1000,
          enabled: true
        })
      }
      configDialogVisible.value = true
    }

    // 保存配置
    const saveConfig = async () => {
      try {
        if (isEditConfig.value) {
          await axios.put(API_BASE + '/config', configForm)
          ElMessage.success('更新成功')
        } else {
          await axios.post(API_BASE + '/config', configForm)
          ElMessage.success('创建成功')
        }
        configDialogVisible.value = false
        await loadConfigs()
      } catch (e) {
        ElMessage.error('保存失败：' + e.message)
      }
    }

    // 删除配置
    const deleteConfig = async (id) => {
      try {
        await ElMessageBox.confirm('确认删除该核对配置？', '提示', { type: 'warning' })
        await axios.delete(API_BASE + '/config/' + id)
        ElMessage.success('删除成功')
        await loadConfigs()
      } catch (e) {
        if (e !== 'cancel') ElMessage.error('删除失败：' + e.message)
      }
    }

    // 切换启用状态
    const toggleEnabled = async (row) => {
      try {
        const payload = { ...row, enabled: !row.enabled }
        await axios.put(API_BASE + '/config', payload)
        ElMessage.success(row.enabled ? '已禁用' : '已启用')
        await loadConfigs()
      } catch (e) {
        ElMessage.error('操作失败：' + e.message)
      }
    }

    // 立即执行核对
    const runNow = async (id) => {
      try {
        await ElMessageBox.confirm('确认立即执行该核对任务？', '提示', { type: 'info' })
        await axios.post(API_BASE + '/run/' + id)
        ElMessage.success('核对任务已启动，请查看执行记录')
        // 切换到执行记录标签
        filterConfigId.value = id
        activeTab.value = 'jobs'
        setTimeout(() => loadJobs(), 1000)
      } catch (e) {
        if (e !== 'cancel') ElMessage.error('执行失败：' + e.message)
      }
    }

    // 加载执行记录
    const loadJobs = async () => {
      if (!filterConfigId.value) {
        jobs.value = []
        return
      }
      jobLoading.value = true
      try {
        const res = await axios.get(API_BASE + '/job/' + filterConfigId.value)
        jobs.value = res.data || []
      } finally {
        jobLoading.value = false
      }
    }

    const getStatusType = (status) => {
      const map = { 'PENDING': 'warning', 'RUNNING': 'primary', 'SUCCESS': 'success', 'FAILED': 'danger' }
      return map[status] || 'info'
    }

    // 查看差异明细
    const viewDiff = async (jobId) => {
      diffFilterConfigId.value = ''
      diffStatusFilter.value = ''
      const res = await axios.get(API_BASE + '/diff/' + jobId)
      diffs.value = res.data || []
      activeTab.value = 'diff'
    }

    // 按配置加载差异明细
    const loadDiffsByConfig = async () => {
      if (!diffFilterConfigId.value) {
        diffs.value = []
        return
      }
      diffLoading.value = true
      try {
        const params = {}
        if (diffStatusFilter.value) params.status = diffStatusFilter.value
        const res = await axios.get(API_BASE + '/diff/config/' + diffFilterConfigId.value, { params })
        diffs.value = res.data || []
      } finally {
        diffLoading.value = false
      }
    }

    // 标记单条差异已修复
    const fixDiff = async (diffId) => {
      try {
        await axios.post(API_BASE + '/diff/fix/' + diffId)
        ElMessage.success('已标记修复')
        await loadDiffsByConfig()
      } catch (e) {
        ElMessage.error('操作失败：' + e.message)
      }
    }

    // 单条补数
    const compensateSingle = (row) => {
      compensateConfigId.value = row.configId
      compensatePkList.value = row.pkValue
      compensateDialogVisible.value = true
    }

    // 执行补偿
    const doCompensate = async () => {
      const pkList = compensatePkList.value.split('\n').map(s => s.trim()).filter(Boolean)
      if (pkList.length === 0) {
        ElMessage.warning('请输入要补偿的主键')
        return
      }
      try {
        await axios.post(API_BASE + '/compensate', {
          configId: compensateConfigId.value,
          pkList: pkList
        })
        ElMessage.success(`补偿任务已提交，共 ${pkList.length} 条数据`)
        compensateDialogVisible.value = false
        await loadDiffsByConfig()
      } catch (e) {
        ElMessage.error('补偿失败：' + e.message)
      }
    }

    onMounted(() => {
      loadConfigs()
      loadDataSources()
    })

    return {
      activeTab,
      loading,
      jobLoading,
      diffLoading,
      configs,
      configDialogVisible,
      isEditConfig,
      configForm,
      dataSources,
      jobs,
      filterConfigId,
      diffs,
      diffFilterConfigId,
      diffStatusFilter,
      compensateDialogVisible,
      compensatePkList,
      compensateConfigId,
      loadConfigs,
      openConfigDialog,
      saveConfig,
      deleteConfig,
      toggleEnabled,
      runNow,
      loadJobs,
      getStatusType,
      viewDiff,
      loadDiffsByConfig,
      fixDiff,
      compensateSingle,
      doCompensate
    }
  }
}
</script>

<style scoped>
.el-tabs {
  border: 1px solid #dcdfe6;
  border-radius: 4px;
}
.el-tabs :deep(.el-tabs__header) {
  margin: 0;
  background: #f5f7fa;
}
.el-tabs :deep(.el-tabs__item) {
  padding: 0 20px;
  height: 40px;
  line-height: 40px;
}
.el-tabs :deep(.el-tabs__content) {
  padding: 16px;
}
.el-form-item {
  margin-bottom: 18px;
}
</style>