import { Link } from 'react-router'

import { isApiError } from '../api/error'
import { routes } from '../app/routes'
import { LoadingState } from '../components/LoadingState'
import { ErrorState } from '../components/StateBlock'
import { notificationSettingLabel } from '../notification/labels'
import { useNotificationSettings, useUpdateNotificationSetting } from '../notification/queries'

/*
 * 알림 설정. 종류마다 켜고 끄는 토글 하나다. 누르면 바로 저장되고 실패하면 되돌아온다.
 * 이메일 발송은 없어 여기서 끄는 것은 앱 안 알림이다. 저장 버튼을 따로 두지 않는다.
 */
export function NotificationSettingsPage() {
  const settings = useNotificationSettings()
  const update = useUpdateNotificationSetting()

  return (
    <section className="view active" id="view-notification-settings">
      <Link className="back" to={routes.notifications}>
        알림으로 돌아가기
      </Link>
      <div className="page-head">
        <div>
          <span className="eyebrow">계정</span>
          <h1>알림 설정</h1>
          <p className="sub">끈 종류는 알림이 만들어지지 않습니다. 바꾸면 바로 저장됩니다.</p>
        </div>
      </div>

      {update.isError && (
        <div className="callout crit" role="alert">
          <span>{isApiError(update.error) ? update.error.message : '설정을 저장하지 못했습니다.'} 이전 값으로 되돌렸습니다. 잠시 후 다시 시도해 주세요.</span>
        </div>
      )}

      {settings.isPending && <LoadingState label="알림 설정을 불러오는 중…" />}
      {settings.isError && (
        <ErrorState title="알림 설정을 불러오지 못했습니다" error={settings.error} onRetry={() => settings.refetch()} />
      )}
      {settings.isSuccess && (
        <div className="card pref-list">
          {settings.data.map((setting) => {
            const label = notificationSettingLabel(setting.type)
            const saving = update.isPending && update.variables?.type === setting.type
            return (
              <label className="pref-row" key={setting.type}>
                <div>
                  <b>{label.title}</b>
                  {label.description !== '' && <span>{label.description}</span>}
                </div>
                <input
                  type="checkbox"
                  role="switch"
                  className="switch"
                  checked={setting.enabled}
                  aria-checked={setting.enabled}
                  aria-busy={saving}
                  disabled={update.isPending}
                  onChange={(event) => update.mutate({ type: setting.type, enabled: event.target.checked })}
                />
              </label>
            )
          })}
        </div>
      )}
    </section>
  )
}
