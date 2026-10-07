import { Link, useParams } from 'react-router'
import { useFatherDetail } from '../../lib/admin'
import { ViewAsContext } from '../../lib/viewAs'
import { Icon } from '../../shared/Icon'
import { ErrorState, Skeleton } from '../../shared/States'
import ui from '../../shared/ui.module.css'
import { FatherHome } from '../father/Home'
import styles from './Admin.module.css'

/**
 * View-as (D-105): the father's home exactly as he sees it - the same component, his data (GET
 * /api/admin/fathers/:id/home is the same server object as his own /api/father/home), every action disabled.
 */
export function FatherViewAs() {
  const { id = '' } = useParams()
  const detail = useFatherDetail(id)
  if (detail.isPending) return <Skeleton lines={4} />
  if (detail.error) return <ErrorState error={detail.error} onRetry={() => detail.refetch()} />
  const name = detail.data.profile.name || 'האבא'
  return (
    <div className={ui.stack}>
      <Link to={`/admin/fathers/${id}`} className={ui.backLink}><Icon name="chevronStart" size={16} /> <bdi>{name}</bdi></Link>
      <div className={styles.banner} role="note">
        <Icon name="eye" size={18} />
        <span className={styles.bannerText}>כך <bdi>{name}</bdi> רואה את הלוח שלו. תצוגה בלבד, אי אפשר לשנות מכאן.</span>
      </div>
      <ViewAsContext.Provider value={{ fatherId: Number(id), name }}>
        <FatherHome />
      </ViewAsContext.Provider>
    </div>
  )
}
