import { createRouter, createWebHistory } from 'vue-router'
import Dashboard from '../views/Dashboard.vue'
import Tasks from '../views/Tasks.vue'
import Cluster from '../views/Cluster.vue'
import MetadataManagement from '../views/MetadataManagement.vue'
import Reconciliation from '../views/Reconciliation.vue'

const routes = [
    { path: '/', redirect: '/dashboard' },
    { path: '/dashboard', component: Dashboard },
    { path: '/metadata', component: MetadataManagement },
    { path: '/tasks', component: Tasks },
    { path: '/cluster', component: Cluster },
    { path: '/reconciliation', component: Reconciliation },
]

const router = createRouter({
    history: createWebHistory(),
    routes
})

export default router