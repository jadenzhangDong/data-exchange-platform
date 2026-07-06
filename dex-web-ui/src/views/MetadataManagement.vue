<template>
  <div>
    <h2 style="margin-bottom:16px;">🗄️ 元数据管理</h2>
    <el-tabs v-model="activeTab" type="border-card">
      <!-- 1. 数据源管理 -->
      <el-tab-pane label="数据源" name="datasource">
        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:16px;">
          <span></span>
          <el-button type="primary" @click="openDsDialog()">+ 新增</el-button>
        </div>
        <el-table :data="datasources" border>
          <el-table-column prop="name" label="名称" />
          <el-table-column prop="type" label="类型" />
          <el-table-column prop="description" label="描述" />
          <el-table-column prop="status" label="状态">
            <template #default="{ row }">
              <el-tag :type="row.status === 'ONLINE' ? 'success' : 'info'">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="280">
            <template #default="{ row }">
              <el-button size="small" type="primary" @click="openDsDialog(row)">编辑</el-button>
              <el-button size="small" type="warning" @click="inspectAndSave(row.id)">探查</el-button>
              <el-button size="small" type="danger" @click="handleDeleteDs(row.id)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 2. 表结构管理 -->
      <el-tab-pane label="表结构" name="tables">
        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:16px;">
          <div>
            <el-select v-model="filterDsId" placeholder="选择数据源" @change="loadTables" style="width:200px;">
              <el-option v-for="ds in datasources" :key="ds.id" :label="ds.name" :value="ds.id" />
            </el-select>
            <el-button type="primary" @click="loadTables" style="margin-left:8px;">刷新</el-button>
            <el-button type="warning" @click="inspectCurrentDs" :disabled="!filterDsId" style="margin-left:8px;" :loading="tableLoading">探查该数据源</el-button>
          </div>
          <span style="color:#909399;font-size:13px;">提示：先探查数据源，再刷新查看表结构</span>
        </div>
        <el-table :data="tables" border v-loading="tableLoading">
          <el-table-column prop="tableName" label="表名" />
          <el-table-column prop="schemaName" label="Schema" />
          <el-table-column prop="tableType" label="类型" />
          <el-table-column prop="rowCount" label="行数" />
          <el-table-column label="字段数" prop="columns" width="100">
            <template #default="{ row }">
              {{ row.columns ? JSON.parse(row.columns).length : 0 }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150">
            <template #default="{ row }">
              <el-button size="small" type="danger" @click="deleteTable(row.id)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div v-if="tables.length === 0 && filterDsId" style="text-align:center;padding:20px;color:#909399;">
          暂无表结构，请先点击“探查该数据源”按钮。
        </div>
      </el-tab-pane>

      <!-- 3. 映射规则管理 -->
      <el-tab-pane label="映射规则" name="mappings">
        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:16px;">
          <span></span>
          <el-button type="primary" @click="openMappingDialog()">+ 新增</el-button>
        </div>
        <el-table :data="mappingRules" border>
          <el-table-column prop="name" label="名称" />
          <el-table-column prop="description" label="描述" />
          <el-table-column prop="sourceTableId" label="源表ID" />
          <el-table-column prop="targetTableId" label="目标表ID" />
          <el-table-column label="操作" width="200">
            <template #default="{ row }">
              <el-button size="small" type="primary" @click="openMappingDialog(row)">编辑</el-button>
              <el-button size="small" type="danger" @click="deleteMapping(row.id)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 4. 任务模板管理 -->
      <el-tab-pane label="任务模板" name="templates">
        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:16px;">
          <span></span>
          <el-button type="primary" @click="openTemplateDialog()">+ 新增模板</el-button>
        </div>
        <el-table :data="templates" border>
          <el-table-column prop="name" label="名称" />
          <el-table-column prop="category" label="分类" />
          <el-table-column prop="mode" label="模式" />
          <el-table-column prop="description" label="描述" />
          <el-table-column label="操作" width="200">
            <template #default="{ row }">
              <el-button size="small" type="primary" @click="openTemplateDialog(row)">编辑</el-button>
              <el-button size="small" type="danger" @click="deleteTemplate(row.id)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- ===== 对话框（数据源） ===== -->
    <el-dialog v-model="dsDialogVisible" :title="isEditDs ? '编辑数据源' : '新增数据源'" width="550px">
      <el-form :model="dsForm" label-width="80px">
        <el-form-item label="名称"><el-input v-model="dsForm.name" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="dsForm.type">
            <el-option label="MySQL" value="mysql" />
            <el-option label="PostgreSQL" value="postgresql" />
            <el-option label="Kafka" value="kafka" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述"><el-input v-model="dsForm.description" /></el-form-item>
        <el-form-item label="配置 JSON">
          <el-input type="textarea" v-model="dsForm.configStr" :rows="4" placeholder='{"host":"localhost","port":3306,"database":"test","user":"root","password":"123"}' />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dsDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveDataSource">保存</el-button>
      </template>
    </el-dialog>

    <!-- ===== 对话框（映射规则） ===== -->
    <el-dialog v-model="mappingDialogVisible" :title="isEditMapping ? '编辑映射规则' : '新增映射规则'" width="600px">
      <el-form :model="mappingForm" label-width="100px">
        <el-form-item label="名称"><el-input v-model="mappingForm.name" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="mappingForm.description" /></el-form-item>
        <el-form-item label="源表ID"><el-input v-model="mappingForm.sourceTableId" /></el-form-item>
        <el-form-item label="目标表ID"><el-input v-model="mappingForm.targetTableId" /></el-form-item>
        <el-form-item label="映射 JSON">
          <el-input type="textarea" v-model="mappingForm.mappingJson" :rows="6" placeholder='[{"source":"id","target":"user_id"}]' />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="mappingDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveMappingRule">保存</el-button>
      </template>
    </el-dialog>

    <!-- ===== 对话框（任务模板） ===== -->
    <el-dialog v-model="templateDialogVisible" :title="isEditTemplate ? '编辑任务模板' : '新增任务模板'" width="750px">
      <el-form :model="templateForm" label-width="120px">
        <el-form-item label="名称"><el-input v-model="templateForm.name" /></el-form-item>
        <el-form-item label="分类"><el-input v-model="templateForm.category" /></el-form-item>
        <el-form-item label="模式">
          <el-select v-model="templateForm.mode">
            <el-option label="批处理" value="BATCH" />
            <el-option label="流式" value="STREAMING" />
            <el-option label="定时" value="SCHEDULED" />
            <el-option label="一次性" value="ONESHOT" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述"><el-input v-model="templateForm.description" /></el-form-item>
        <el-form-item label="Source 模板">
          <el-input type="textarea" v-model="templateForm.sourceTemplate" :rows="3" placeholder='{"type":"jdbc-polling","params":{...}}' />
        </el-form-item>
        <el-form-item label="Sink 模板">
          <el-input type="textarea" v-model="templateForm.sinkTemplate" :rows="3" />
        </el-form-item>
        <el-form-item label="Transform 模板">
          <el-input type="textarea" v-model="templateForm.transformTemplates" :rows="3" placeholder='[{"type":"field-mapper","params":{...}}]' />
        </el-form-item>
        <el-form-item label="默认批次大小">
          <el-input-number v-model="templateForm.defaultBatchSize" :min="1" />
        </el-form-item>
        <el-form-item label="默认 Cron">
          <el-input v-model="templateForm.defaultCron" placeholder="0 0 2 * * ?" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="templateDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveTemplate">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import { ref, onMounted, reactive } from 'vue'
import {
  getDatasources, createDatasource, updateDatasource, deleteDatasource,
  getTablesByDataSource, deleteTableMeta,
  getMappingRules, createMappingRule, updateMappingRule, deleteMappingRule,
  getTemplates, createTemplate, updateTemplate, deleteTemplate,
  inspectAndSave
} from '../api'
import { ElMessage } from 'element-plus'

export default {
  setup() {
    const activeTab = ref('datasource')

    // ===== 数据源 =====
    const datasources = ref([])
    const dsDialogVisible = ref(false)
    const isEditDs = ref(false)
    const dsForm = reactive({ id: null, name: '', type: 'mysql', description: '', configStr: '{}', status: 'ONLINE' })

    const loadDs = async () => {
      datasources.value = await getDatasources()
    }

    const openDsDialog = (row) => {
      if (row) {
        isEditDs.value = true
        Object.assign(dsForm, { ...row, configStr: row.config || '{}' })
      } else {
        isEditDs.value = false
        Object.assign(dsForm, { id: null, name: '', type: 'mysql', description: '', configStr: '{}', status: 'ONLINE' })
      }
      dsDialogVisible.value = true
    }

    const saveDataSource = async () => {
      try {
        const payload = { ...dsForm }
        payload.config = payload.configStr
        delete payload.configStr
        if (isEditDs.value) {
          await updateDatasource(payload)
          ElMessage.success('更新成功')
        } else {
          await createDatasource(payload)
          ElMessage.success('创建成功')
        }
        dsDialogVisible.value = false
        await loadDs()
      } catch (e) {
        ElMessage.error('保存失败：' + e.message)
      }
    }

    const handleDeleteDs = async (id) => {
      if (!confirm('确认删除？')) return
      await deleteDatasource(id)
      ElMessage.success('删除成功')
      await loadDs()
    }

    // ===== 表结构 =====
    const tables = ref([])
    const filterDsId = ref('')
    const tableLoading = ref(false)

    const loadTables = async () => {
      if (!filterDsId.value) {
        tables.value = []
        return
      }
      tableLoading.value = true
      try {
        tables.value = await getTablesByDataSource(filterDsId.value)
      } catch (e) {
        ElMessage.error('加载表结构失败：' + e.message)
      } finally {
        tableLoading.value = false
      }
    }

    const deleteTable = async (id) => {
      if (!confirm('确认删除该表元数据？')) return
      await deleteTableMeta(id)
      ElMessage.success('删除成功')
      await loadTables()
    }

    // ===== 映射规则 =====
    const mappingRules = ref([])
    const mappingDialogVisible = ref(false)
    const isEditMapping = ref(false)
    const mappingForm = reactive({ id: null, name: '', description: '', sourceTableId: '', targetTableId: '', mappingJson: '[]' })

    const loadMappings = async () => {
      mappingRules.value = await getMappingRules()
    }

    const openMappingDialog = (row) => {
      if (row) {
        isEditMapping.value = true
        Object.assign(mappingForm, row)
      } else {
        isEditMapping.value = false
        Object.assign(mappingForm, { id: null, name: '', description: '', sourceTableId: '', targetTableId: '', mappingJson: '[]' })
      }
      mappingDialogVisible.value = true
    }

    const saveMappingRule = async () => {
      try {
        if (isEditMapping.value) {
          await updateMappingRule(mappingForm)
          ElMessage.success('更新成功')
        } else {
          await createMappingRule(mappingForm)
          ElMessage.success('创建成功')
        }
        mappingDialogVisible.value = false
        await loadMappings()
      } catch (e) {
        ElMessage.error('保存失败：' + e.message)
      }
    }

    const deleteMapping = async (id) => {
      if (!confirm('确认删除？')) return
      await deleteMappingRule(id)
      ElMessage.success('删除成功')
      await loadMappings()
    }

    // ===== 任务模板 =====
    const templates = ref([])
    const templateDialogVisible = ref(false)
    const isEditTemplate = ref(false)
    const templateForm = reactive({
      id: null,
      name: '',
      category: '',
      mode: 'BATCH',
      description: '',
      sourceTemplate: '{}',
      sinkTemplate: '{}',
      transformTemplates: '[]',    // 默认空数组
      defaultBatchSize: 1000,
      defaultCron: ''
    })

    const loadTemplates = async () => {
      templates.value = await getTemplates()
    }

    const openTemplateDialog = (row) => {
      if (row) {
        isEditTemplate.value = true
        Object.assign(templateForm, {
          ...row,
          transformTemplates: row.transformTemplates || '[]'
        })
      } else {
        isEditTemplate.value = false
        Object.assign(templateForm, {
          id: null,
          name: '',
          category: '',
          mode: 'BATCH',
          description: '',
          sourceTemplate: '{}',
          sinkTemplate: '{}',
          transformTemplates: '[]',
          defaultBatchSize: 1000,
          defaultCron: ''
        })
      }
      templateDialogVisible.value = true
    }

    const saveTemplate = async () => {
      try {
        // 处理空字符串
        if (!templateForm.transformTemplates || templateForm.transformTemplates.trim() === '') {
          templateForm.transformTemplates = '[]'
        }
        if (!templateForm.sourceTemplate || templateForm.sourceTemplate.trim() === '') {
          templateForm.sourceTemplate = '{}'
        }
        if (!templateForm.sinkTemplate || templateForm.sinkTemplate.trim() === '') {
          templateForm.sinkTemplate = '{}'
        }

        // 验证 JSON 格式
        JSON.parse(templateForm.sourceTemplate)
        JSON.parse(templateForm.sinkTemplate)
        JSON.parse(templateForm.transformTemplates)
      } catch (e) {
        ElMessage.error('JSON 格式错误：' + e.message)
        return
      }

      try {
        if (isEditTemplate.value) {
          await updateTemplate(templateForm)
          ElMessage.success('更新成功')
        } else {
          await createTemplate(templateForm)
          ElMessage.success('创建成功')
        }
        templateDialogVisible.value = false
        await loadTemplates()
      } catch (e) {
        ElMessage.error('保存失败：' + e.message)
      }
    }

    const deleteTemplate = async (id) => {
      if (!confirm('确认删除？')) return
      await deleteTemplate(id)
      ElMessage.success('删除成功')
      await loadTemplates()
    }

    const inspectCurrentDs = async () => {
      if (!filterDsId.value) {
        ElMessage.warning('请先选择数据源')
        return
      }
      tableLoading.value = true
      try {
        const res = await fetch(`/api/meta/datasource/${filterDsId.value}/inspect-and-save`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' }
        })
        if (res.ok) {
          const data = await res.json()
          ElMessage.success(`探查成功，共 ${data.length} 张表`)
          await loadTables()
        } else {
          const errText = await res.text()
          ElMessage.error('探查失败：' + errText)
        }
      } catch (e) {
        ElMessage.error('请求失败：' + e.message)
      } finally {
        tableLoading.value = false
      }
    }

    onMounted(() => {
      loadDs()
      loadMappings()
      loadTemplates()
    })

    return {
      activeTab,
      datasources,
      dsDialogVisible,
      isEditDs,
      dsForm,
      openDsDialog,
      saveDataSource,
      handleDeleteDs,
      inspectAndSave,
      tables,
      filterDsId,
      loadTables,
      deleteTable,
      inspectCurrentDs,
      tableLoading,
      mappingRules,
      mappingDialogVisible,
      isEditMapping,
      mappingForm,
      openMappingDialog,
      saveMappingRule,
      deleteMapping,
      loadMappings,
      templates,
      templateDialogVisible,
      isEditTemplate,
      templateForm,
      openTemplateDialog,
      saveTemplate,
      deleteTemplate,
      loadTemplates,
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
</style>