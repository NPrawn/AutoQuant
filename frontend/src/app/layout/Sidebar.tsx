import { Link, NavLink } from 'react-router'

import { navSections } from '../navigation'
import { routes } from '../routes'

export function Sidebar() {
  return (
    <aside className="rail">
      {/* 로고는 base.css 가 ::before/::after 로 그린다. 상승·하락 두 색 막대다. */}
      <Link to={routes.home} className="brandmark" aria-label="AutoQuant 홈">
        <span className="logo" aria-hidden="true" />
        <span>
          <b>AutoQuant</b>
          <small>CRYPTO STRATEGY</small>
        </span>
      </Link>

      {navSections.map((section) => (
        <nav key={section.label} className="navsec" aria-label={section.label}>
          <span className="lbl">{section.label}</span>
          {section.items.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) => (isActive ? 'navbtn active' : 'navbtn')}
            >
              <svg
                viewBox="0 0 24 24"
                fill="none"
                strokeWidth="1.8"
                strokeLinecap="round"
                strokeLinejoin="round"
                aria-hidden="true"
              >
                {item.icon}
              </svg>
              {item.label}
            </NavLink>
          ))}
        </nav>
      ))}

      <div className="rail-foot">
        <span className="st">
          <i className="dot" aria-hidden="true" />
          Upbit · KRW 연결됨
        </span>
        <small>거래대금 상위 30 · 1일봉</small>
      </div>
    </aside>
  )
}
