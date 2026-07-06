<template>
  <el-container style="height: 100vh;">
    <el-header style="background:#409EFF;color:#fff;display:flex;align-items:center;justify-content:space-between;padding:0 24px;">
      <h1 style="font-size:20px;margin:0;">📊 DEX 数据交换平台</h1>
      <div>
        <span>Master: <el-tag size="small" type="success" id="master-tag">--</el-tag></span>
        <span style="margin-left:16px;">Worker: <span id="worker-count">0</span></span>
      </div>
    </el-header>
    <el-container>
      <el-aside width="200px" style="background:#fff;border-right:1px solid #e4e7ed;">
        <el-menu :default-active="$route.path" router background-color="#fff" text-color="#303133" active-text-color="#409EFF">
          <el-menu-item index="/dashboard">📈 总览</el-menu-item>
          <el-menu-item index="/metadata">🗄️ 元数据管理</el-menu-item>
          <el-menu-item index="/tasks">📋 任务管理</el-menu-item>
          <el-menu-item index="/reconciliation">🔍 核对管理</el-menu-item>
          <el-menu-item index="/cluster">🔧 集群监控</el-menu-item>
        </el-menu>
      </el-aside>
      <el-main style="background:#f0f2f5;padding:20px;">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script>
import { onMounted } from 'vue'
import { getMasterStatus, getWorkers } from './api'

export default {
  setup() {
    const updateStatus = async () => {
      try {
        const status = await getMasterStatus()
        document.getElementById('master-tag').innerText = status
        const workers = await getWorkers()
        document.getElementById('worker-count').innerText = workers.length
      } catch(e) {}
    }
    onMounted(() => {
      updateStatus()
      setInterval(updateStatus, 5000)
    })
  }
}
</script>