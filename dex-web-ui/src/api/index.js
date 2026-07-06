import axios from 'axios'

const api = axios.create({
    baseURL: '/api',
    timeout: 10000,
})

// ====== 数据源 ======
export const getDatasources = () => api.get('/meta/datasource').then(res => res.data)
export const createDatasource = (data) => api.post('/meta/datasource', data).then(res => res.data)
export const updateDatasource = (data) => api.put('/meta/datasource', data).then(res => res.data)
export const deleteDatasource = (id) => api.delete(`/meta/datasource/${id}`).then(res => res.data)
export const inspectDatasource = (id) => api.get(`/meta/datasource/${id}/inspect`).then(res => res.data)

// ====== 任务 ======
export const submitTask = (task) => api.post('/master/task/submit', task).then(res => res.data)
export const stopTask = (taskId) => api.post(`/master/task/stop?taskId=${taskId}`).then(res => res.data)
export const getTaskList = () => api.get('/web/task/list').then(res => res.data)
export const getTaskInstances = (taskId) => api.get(`/web/task/instances/${taskId}`).then(res => res.data)

// ====== 集群 ======
export const getWorkers = () => api.get('/web/cluster/workers').then(res => res.data)
export const getMasterStatus = () => api.get('/master/status').then(res => res.data)
export const switchMaster = () => api.post('/web/operation/switch-master').then(res => res.data)

// ====== 生成任务配置 ======
export const generateTaskConfig = (template) => api.post('/meta/task/generate', template).then(res => res.data)

// 删除任务
export const deleteTask = (taskId) => api.delete(`/master/task/${taskId}`).then(res => res.data)
// 更新任务
export const updateTask = (task) => api.put('/master/task/update', task).then(res => res.data)

// 启用任务
export const enableTask = (taskId) => api.post(`/master/task/enable?taskId=${taskId}`).then(res => res.data)
// 禁用任务
export const disableTask = (taskId) => api.post(`/master/task/disable?taskId=${taskId}`).then(res => res.data)

// ====== 模板管理 ======
export const getTemplates = () => api.get('/meta/template').then(res => res.data)
export const createTemplate = (data) => api.post('/meta/template', data).then(res => res.data)
export const updateTemplate = (data) => api.put('/meta/template', data).then(res => res.data)
export const deleteTemplate = (id) => api.delete(`/meta/template/${id}`).then(res => res.data)
export const generateTaskFromTemplate = (params) => api.post('/meta/template/generate', params).then(res => res.data)

// ====== 表元数据 ======
export const getTablesByDataSource = (dsId) => api.get(`/meta/table/datasource/${dsId}`).then(res => res.data)
export const deleteTableMeta = (id) => api.delete(`/meta/table/${id}`).then(res => res.data)

// ====== 映射规则 ======
export const getMappingRules = () => api.get('/meta/mapping').then(res => res.data)
export const createMappingRule = (data) => api.post('/meta/mapping', data).then(res => res.data)
export const updateMappingRule = (data) => api.put('/meta/mapping', data).then(res => res.data)
export const deleteMappingRule = (id) => api.delete(`/meta/mapping/${id}`).then(res => res.data)
// ====== 元数据管理（表探查） ======
export const inspectAndSave = (dataSourceId) => api.post(`/meta/datasource/${dataSourceId}/inspect-and-save`).then(res => res.data)