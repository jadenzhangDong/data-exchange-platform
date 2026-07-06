<template>
  <div>
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:16px;">
      <h2>📋 任务管理</h2>
      <div>
        <el-button type="primary" @click="openWizard()">+ 从模板生成</el-button>
        <el-button type="success" @click="openJsonEditor()">+ 直接 JSON</el-button>
      </div>
    </div>

    <!-- 任务列表 -->
    <el-table :data="tasks" border>
      <el-table-column prop="taskId" label="任务ID" width="180" />
      <el-table-column prop="taskName" label="任务名称" />
      <el-table-column prop="mode" label="模式" />
      <el-table-column prop="status" label="状态">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="380">
        <template #default="{ row }">
          <el-button size="small" type="primary" @click="editTask(row)">编辑</el-button>
          <el-button size="small" @click="viewInstances(row.taskId)">实例</el-button>
          <el-button size="small" type="success" @click="handleEnable(row.taskId)" v-if="row.status === 'DISABLED'">启用</el-button>
          <el-button size="small" type="warning" @click="handleDisable(row.taskId)" v-if="row.status === 'ENABLED'">禁用</el-button>
          <el-button size="small" type="danger" @click="handleDelete(row.taskId)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 任务实例 -->
    <el-dialog v-model="instanceDialogVisible" title="任务实例" width="800px">
      <el-table :data="instances" border>
        <el-table-column prop="instanceId" label="实例ID" width="200" />
        <el-table-column prop="state" label="状态">
          <template #default="{ row }">
            <el-tag :type="row.state === 'SUCCESS' ? 'success' : row.state === 'FAILED' ? 'danger' : 'warning'">{{ row.state }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="assignedWorkerId" label="Worker" />
        <el-table-column prop="processedRecords" label="处理记录数" />
        <el-table-column prop="startTime" label="开始时间" />
        <el-table-column prop="endTime" label="结束时间" />
      </el-table>
    </el-dialog>

    <!-- ========== 任务生成向导 ========== -->
    <el-dialog v-model="wizardVisible" title="从模板生成任务" width="900px" :close-on-click-modal="false">
      <el-steps :active="step" finish-status="success" align-center>
        <el-step title="选择模板" />
        <el-step title="选择源表" />
        <el-step title="选择目标" />
        <el-step title="映射规则" />
        <el-step title="预览提交" />
      </el-steps>

      <div style="margin-top:20px;">
        <!-- 步骤1：选择模板 -->
        <div v-if="step === 0">
          <el-form label-width="100px">
            <el-form-item label="任务模板">
              <el-select v-model="wizard.templateId" placeholder="请选择模板" style="width:300px;" @change="onTemplateChange">
                <el-option v-for="tpl in templates" :key="tpl.id" :label="tpl.name + ' (' + tpl.mode + ')'" :value="tpl.id" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="selectedTemplate" label="模板描述">
              <span>{{ selectedTemplate.description }}</span>
            </el-form-item>
            <el-form-item v-if="selectedTemplate" label="Source 模板">
              <pre style="background:#f5f7fa;padding:8px;border-radius:4px;font-size:12px;">{{ selectedTemplate.sourceTemplate }}</pre>
            </el-form-item>
          </el-form>
          <div style="margin-top:16px;">
            <el-button type="primary" @click="step = 1" :disabled="!wizard.templateId">下一步</el-button>
          </div>
        </div>

        <!-- 步骤2：选择源表 -->
        <div v-if="step === 1">
          <el-form label-width="100px">
            <el-form-item label="源数据源">
              <el-select v-model="wizard.sourceDsId" placeholder="选择数据源" style="width:300px;" @change="loadSourceTables">
                <el-option v-for="ds in datasources" :key="ds.id" :label="ds.name" :value="ds.id" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="sourceTables.length > 0" label="选择表">
              <el-select v-model="wizard.sourceTableId" placeholder="选择表" style="width:300px;" @change="onSourceTableChange">
                <el-option v-for="tbl in sourceTables" :key="tbl.id" :label="tbl.tableName" :value="tbl.id" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="selectedSourceTable" label="字段选择">
              <el-checkbox-group v-model="wizard.selectedFields">
                <el-checkbox v-for="col in selectedSourceTableColumns" :key="col.columnName" :label="col.columnName" />
              </el-checkbox-group>
            </el-form-item>
          </el-form>
          <div style="margin-top:16px;">
            <el-button @click="step = 0">上一步</el-button>
            <el-button type="primary" @click="step = 2" :disabled="!wizard.sourceTableId || wizard.selectedFields.length === 0">下一步</el-button>
          </div>
        </div>

        <!-- 步骤3：选择目标 -->
        <div v-if="step === 2">
          <el-form label-width="100px">
            <el-form-item label="目标数据源">
              <el-select v-model="wizard.targetDsId" placeholder="选择数据源" style="width:300px;">
                <el-option v-for="ds in datasources" :key="ds.id" :label="ds.name" :value="ds.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="目标表名">
              <el-input v-model="wizard.targetTableName" placeholder="请输入目标表名" style="width:300px;" />
            </el-form-item>
          </el-form>
          <div style="margin-top:16px;">
            <el-button @click="step = 1">上一步</el-button>
            <el-button type="primary" @click="step = 3" :disabled="!wizard.targetDsId || !wizard.targetTableName">下一步</el-button>
          </div>
        </div>

        <!-- 步骤4：映射规则 -->
        <div v-if="step === 3">
          <el-form label-width="100px">
            <el-form-item label="映射规则">
              <el-select v-model="wizard.mappingRuleId" placeholder="选择映射规则（可选）" style="width:300px;" clearable>
                <el-option v-for="rule in mappingRules" :key="rule.id" :label="rule.name" :value="rule.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="批次大小">
              <el-input-number v-model="wizard.batchSize" :min="1" style="width:150px;" />
            </el-form-item>
            <el-form-item label="Cron 表达式" v-if="selectedTemplate && selectedTemplate.mode === 'SCHEDULED'">
              <el-input v-model="wizard.cron" placeholder="0 0 2 * * ?" style="width:300px;" />
            </el-form-item>
          </el-form>
          <div style="margin-top:16px;">
            <el-button @click="step = 2">上一步</el-button>
            <el-button type="primary" @click="step = 4">下一步</el-button>
          </div>
        </div>

        <!-- 步骤5：预览并提交 -->
        <div v-if="step === 4">
          <el-form label-width="100px">
            <el-form-item label="任务名称">
              <el-input v-model="wizard.taskName" placeholder="输入任务名称" style="width:300px;" />
            </el-form-item>
            <el-form-item label="生成的配置">
              <el-input type="textarea" v-model="generatedConfigJson" :rows="15" style="font-family:monospace;font-size:13px;" readonly />
            </el-form-item>
          </el-form>
          <div style="margin-top:16px;">
            <el-button @click="step = 3">上一步</el-button>
            <el-button type="success" @click="submitGeneratedTask">提交任务</el-button>
          </div>
        </div>
      </div>
    </el-dialog>

    <!-- ========== JSON 编辑器对话框 ========== -->
    <el-dialog v-model="jsonDialogVisible" title="编辑任务配置" width="700px">
      <el-input type="textarea" v-model="taskJson" :rows="20" style="font-family:monospace;font-size:13px;" />
      <template #footer>
        <el-button @click="jsonDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitJsonTask">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import { ref, onMounted, reactive, computed, watch } from 'vue'
import {
  getTaskList, getTaskInstances, stopTask, deleteTask, updateTask, submitTask, enableTask, disableTask,
  getTemplates, getDatasources, getTablesByDataSource, getMappingRules, generateTaskFromTemplate
} from '../api'
import { ElMessage } from 'element-plus'

export default {
  setup() {
    const tasks = ref([])
    const instances = ref([])
    const instanceDialogVisible = ref(false)

    // 向导
    const wizardVisible = ref(false)
    const step = ref(0)
    const wizard = reactive({
      templateId: '',
      sourceDsId: '',
      sourceTableId: '',
      targetDsId: '',
      targetTableName: '',
      mappingRuleId: '',
      selectedFields: [],
      batchSize: 1000,
      cron: '',
      taskName: ''
    })

    // 数据
    const templates = ref([])
    const datasources = ref([])
    const sourceTables = ref([])
    const mappingRules = ref([])
    const generatedConfig = ref(null)

    // 选中的模板对象
    const selectedTemplate = computed(() => templates.value.find(t => t.id === wizard.templateId))
    // 选中的源表对象
    const selectedSourceTable = computed(() => sourceTables.value.find(t => t.id === wizard.sourceTableId))
    const selectedSourceTableColumns = computed(() => {
      if (!selectedSourceTable.value) return []
      try {
        return JSON.parse(selectedSourceTable.value.columns) || []
      } catch { return [] }
    })
    // 生成的配置 JSON 字符串
    const generatedConfigJson = ref('')

    // 加载数据
    const loadTasks = async () => {
      tasks.value = await getTaskList()
    }
    const loadTemplates = async () => {
      templates.value = await getTemplates()
    }
    const loadDatasources = async () => {
      datasources.value = await getDatasources()
    }
    const loadMappingRules = async () => {
      mappingRules.value = await getMappingRules()
    }
    const loadSourceTables = async () => {
      if (!wizard.sourceDsId) {
        sourceTables.value = []
        return
      }
      sourceTables.value = await getTablesByDataSource(wizard.sourceDsId)
    }
    const onSourceTableChange = () => {
      // 清空之前选择的字段
      wizard.selectedFields = []
    }
    const onTemplateChange = () => {
      // 如果模板有默认批次大小，自动填充
      if (selectedTemplate.value && selectedTemplate.value.defaultBatchSize) {
        wizard.batchSize = selectedTemplate.value.defaultBatchSize
      }
      if (selectedTemplate.value && selectedTemplate.value.defaultCron) {
        wizard.cron = selectedTemplate.value.defaultCron
      }
    }

    // 打开向导
    const openWizard = async () => {
      await loadTemplates()
      await loadDatasources()
      await loadMappingRules()
      // 重置向导状态
      step.value = 0
      wizard.templateId = ''
      wizard.sourceDsId = ''
      wizard.sourceTableId = ''
      wizard.targetDsId = ''
      wizard.targetTableName = ''
      wizard.mappingRuleId = ''
      wizard.selectedFields = []
      wizard.batchSize = 1000
      wizard.cron = ''
      wizard.taskName = ''
      sourceTables.value = []
      generatedConfig.value = null
      generatedConfigJson.value = ''
      wizardVisible.value = true
    }

    // 监听步骤变化，当进入预览步骤时生成配置
    watch(step, async (newVal) => {
      if (newVal === 4 && wizard.templateId && wizard.sourceTableId && wizard.targetDsId && wizard.targetTableName) {
        try {
          const params = {
            templateId: wizard.templateId,
            sourceTableId: wizard.sourceTableId,
            targetTableId: wizard.targetTableId || '',   // 可能为空
            mappingRuleId: wizard.mappingRuleId || '',
            batchSize: String(wizard.batchSize),
            cron: wizard.cron || '',
            targetTableName: wizard.targetTableName      // 添加此参数
          }
          const resp = await generateTaskFromTemplate(params)
          generatedConfig.value = resp.taskConfig
          generatedConfigJson.value = JSON.stringify(resp.taskConfig, null, 2)
          if (!wizard.taskName) {
            const tplName = selectedTemplate.value?.name || '任务'
            const tableName = selectedSourceTable.value?.tableName || '表'
            wizard.taskName = `${tplName}-${tableName}`
          }
        } catch (e) {
          ElMessage.error('生成配置失败：' + e.message)
        }
      }
    })

    const submitGeneratedTask = async () => {
      if (!generatedConfig.value) {
        ElMessage.error('未生成配置')
        return
      }
      try {
        // 将任务名称覆盖
        generatedConfig.value.taskName = wizard.taskName || generatedConfig.value.taskName
        await submitTask(generatedConfig.value)
        ElMessage.success('任务提交成功')
        wizardVisible.value = false
        await loadTasks()
      } catch (e) {
        ElMessage.error('提交失败：' + e.message)
      }
    }

    // JSON 编辑器
    const jsonDialogVisible = ref(false)
    const taskJson = ref('')
    const isEditMode = ref(false)
    const editingTaskId = ref(null)

    const openJsonEditor = () => {
      isEditMode.value = false
      editingTaskId.value = null
      taskJson.value = JSON.stringify({
        taskId: 'task-' + Date.now(),
        taskName: '示例任务',
        mode: 'BATCH',
        batchSize: 1000,
        source: { type: 'mock', params: {} },
        sink: { type: 'mock', params: {} }
      }, null, 2)
      jsonDialogVisible.value = true
    }

    const submitJsonTask = async () => {
      try {
        const config = JSON.parse(taskJson.value)
        if (isEditMode.value) {
          config.taskId = editingTaskId.value
          await updateTask(config)
          ElMessage.success('更新成功')
        } else {
          await submitTask(config)
          ElMessage.success('提交成功')
        }
        jsonDialogVisible.value = false
        await loadTasks()
      } catch (e) {
        ElMessage.error('提交失败：' + e.message)
      }
    }

    const editTask = (row) => {
      isEditMode.value = true
      editingTaskId.value = row.taskId
      try {
        taskJson.value = JSON.stringify(JSON.parse(row.configJson), null, 2)
      } catch {
        taskJson.value = ''
      }
      jsonDialogVisible.value = true
    }

    // 启用/禁用/删除
    const handleEnable = async (taskId) => {
      await enableTask(taskId)
      ElMessage.success('启用成功')
      await loadTasks()
    }
    const handleDisable = async (taskId) => {
      if (!confirm('确认禁用该任务？')) return
      await disableTask(taskId)
      ElMessage.success('禁用成功')
      await loadTasks()
    }
    const handleDelete = async (taskId) => {
      if (!confirm('确认删除？')) return
      await deleteTask(taskId)
      ElMessage.success('删除成功')
      await loadTasks()
    }
    const viewInstances = async (taskId) => {
      instances.value = await getTaskInstances(taskId)
      instanceDialogVisible.value = true
    }

    onMounted(() => {
      loadTasks()
    })

    return {
      tasks,
      instances,
      instanceDialogVisible,
      wizardVisible,
      step,
      wizard,
      templates,
      datasources,
      sourceTables,
      mappingRules,
      selectedTemplate,
      selectedSourceTable,
      selectedSourceTableColumns,
      generatedConfigJson,
      openWizard,
      loadSourceTables,
      onSourceTableChange,
      onTemplateChange,
      submitGeneratedTask,
      jsonDialogVisible,
      taskJson,
      isEditMode,
      editingTaskId,
      openJsonEditor,
      submitJsonTask,
      editTask,
      handleEnable,
      handleDisable,
      handleDelete,
      viewInstances,
      loadTasks
    }
  }
}
</script>

<style scoped>
pre {
  background: #f5f7fa;
  padding: 8px;
  border-radius: 4px;
  font-size: 12px;
}
</style>