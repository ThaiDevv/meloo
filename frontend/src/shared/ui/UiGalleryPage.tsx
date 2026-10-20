import { useTranslation } from 'react-i18next'
import { Button } from './Button'
import { Placeholder } from './Placeholder'

/** Component gallery. FE-01 fills it. Only routed in dev and preview. */
export function UiGalleryPage() {
  const { t } = useTranslation('common')
  return (
    <div className="space-y-4 p-6">
      <Placeholder ns="common" ticket="FE-01" route="/_ui" />
      <div className="flex justify-center gap-2">
        <Button>{t('gallery.primary')}</Button>
        <Button variant="ghost">{t('gallery.ghost')}</Button>
      </div>
    </div>
  )
}
