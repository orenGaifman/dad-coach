import { Route, Routes } from 'react-router'
import { AdminLayout, FatherLayout, NotFound, RootRedirect } from './shell/Layouts'
import { Consume } from './pages/auth/Consume'
import { Login } from './pages/auth/Login'
import { FatherHome } from './pages/father/Home'
import { Sessions } from './pages/father/Sessions'
import { Children } from './pages/father/Children'
import { ProgressPage } from './pages/father/Progress'
import { SettingsPage } from './pages/father/Settings'
import { TrainingPage } from './pages/father/Training'
import { AdminOverview } from './pages/admin/Overview'
import { Fathers } from './pages/admin/Fathers'
import { FatherDetailPage } from './pages/admin/FatherDetail'
import { FatherViewAs } from './pages/admin/FatherViewAs'
import { IntegrationsPage } from './pages/admin/Integrations'
import { Undelivered } from './pages/admin/Undelivered'
import { TemplatesPage } from './pages/admin/Templates'
import { Deletions } from './pages/admin/Deletions'
import { AdminTrainingPage } from './pages/admin/Training'
import { DataDeletion, Privacy, Terms } from './pages/legal/Legal'

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<RootRedirect />} />
      <Route path="/login" element={<Login />} />
      <Route path="/auth/consume" element={<Consume />} />
      <Route path="/privacy" element={<Privacy />} />
      <Route path="/terms" element={<Terms />} />
      <Route path="/data-deletion" element={<DataDeletion />} />
      <Route element={<FatherLayout />}>
        <Route path="/home" element={<FatherHome />} />
        <Route path="/sessions" element={<Sessions />} />
        <Route path="/children" element={<Children />} />
        <Route path="/progress" element={<ProgressPage />} />
        <Route path="/settings" element={<SettingsPage />} />
        <Route path="/training" element={<TrainingPage />} />
      </Route>
      <Route path="/admin" element={<AdminLayout />}>
        <Route index element={<AdminOverview />} />
        <Route path="fathers" element={<Fathers />} />
        <Route path="fathers/:id" element={<FatherDetailPage />} />
        <Route path="fathers/:id/home" element={<FatherViewAs />} />
        <Route path="undelivered" element={<Undelivered />} />
        <Route path="templates" element={<TemplatesPage />} />
        <Route path="integrations" element={<IntegrationsPage />} />
        <Route path="deletions" element={<Deletions />} />
        <Route path="training" element={<AdminTrainingPage />} />
        <Route path="*" element={<NotFound inShell />} />
      </Route>
      <Route path="*" element={<NotFound />} />
    </Routes>
  )
}
