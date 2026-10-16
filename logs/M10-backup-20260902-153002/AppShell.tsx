import {
  BarChart3,
  CalendarDays,
  ChartNoAxesCombined,
  Columns3,
  LayoutDashboard,
  Menu,
  SearchCheck,
  Settings,
  Shield,
  ShoppingCart,
  SlidersHorizontal,
  Users,
} from 'lucide-react';
import { Link, NavLink } from 'react-router-dom';
import { useEffect, useRef, useState, type MouseEvent, type PropsWithChildren } from 'react';
import { IconButton } from '@/components/ui';
import { useAuth } from '@/auth/useAuth';
import { GlobalSearch } from './GlobalSearch';
import styles from './AppShell.module.css';
const items = [
  ['Dashboard', LayoutDashboard, '/'],
  ['Giocatori', SearchCheck, '/players'],
  ['Asta', SlidersHorizontal, '/auction'],
  ['Live Auction', ShoppingCart, '/auction/live'],
  ['Rosa', Shield, '/squad'],
  ['Formazione', ChartNoAxesCombined, '/lineup'],
  ['Calendario', CalendarDays, '/calendar'],
  ['Scouting', SearchCheck, '/scouting'],
  ['Analisi', ChartNoAxesCombined, '/analysis'],
  ['Statistiche', BarChart3, '/statistics'],
  ['Comparatore', Columns3, '/comparator'],
  ['Market', ShoppingCart, '/market'],
  ['Impostazioni', Settings, '/settings'],
] as const;
export function AppShell({ children }: PropsWithChildren) {
  const auth = useAuth();
  const [menuOpen, setMenuOpen] = useState(false);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(true);
  const menuButton = useRef<HTMLButtonElement>(null);
  useEffect(() => {
    if (!menuOpen) return;
    const close = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setMenuOpen(false);
        menuButton.current?.focus();
      }
    };
    document.addEventListener('keydown', close);
    return () => document.removeEventListener('keydown', close);
  }, [menuOpen]);
  const collapseNavigation = (event: MouseEvent<HTMLAnchorElement>) => {
    setSidebarCollapsed(true);
    setMenuOpen(false);
    event.currentTarget.blur();
  };
  const navItems = auth.isAdmin
    ? [...items, ['Amministrazione', Settings, '/admin'] as const]
    : items;
  return (
    <div className={`${styles.shell} ${sidebarCollapsed ? styles.shellCollapsed : ''}`}>
      {menuOpen && (
        <button
          className={styles.backdrop}
          aria-label="Chiudi menu"
          onClick={() => setMenuOpen(false)}
        />
      )}
      <aside
        className={`${styles.sidebar} ${sidebarCollapsed && !menuOpen ? styles.sidebarCollapsed : ''} ${menuOpen ? styles.sidebarOpen : ''}`}
        aria-label="Navigazione principale"
        data-collapsed={sidebarCollapsed && !menuOpen ? 'true' : 'false'}
      >
        <NavLink to="/" className={styles.brand} onClick={collapseNavigation} title="Dashboard">
          <span className={styles.mark}>FI</span>
          <span className={styles.brandText}>
            Fantacalcio
            <br />
            Intelligence
          </span>
        </NavLink>
        <nav className={styles.nav} aria-label="Navigazione principale">
          {navItems.map(([label, Icon, to]) => (
            <NavLink
              key={label}
              to={to}
              end={to === '/'}
              aria-label={label}
              title={label}
              onClick={collapseNavigation}
              className={({ isActive }) => `${styles.navLink} ${isActive ? styles.active : ''}`}
            >
              <Icon size={19} aria-hidden="true" />
              <span>{label}</span>
            </NavLink>
          ))}
        </nav>
      </aside>
      <div className={styles.body}>
        <header className={styles.topbar}>
          <div className={styles.menu}>
            <IconButton
              ref={menuButton}
              label={menuOpen ? 'Chiudi menu' : 'Apri menu'}
              aria-expanded={menuOpen}
              onClick={() => setMenuOpen((open) => !open)}
            >
              <Menu size={18} />
            </IconButton>
          </div>
          <div className={styles.topbarSearch}>
            <GlobalSearch />
          </div>
          <div className={styles.league}>
            <span className={styles.profile}>
              <Users size={16} aria-hidden="true" />
              {auth.status === 'authenticated' ? 'Workspace personale' : 'Accesso pubblico'}
            </span>
          </div>
          <div className={styles.topActions}>
            <Link
              className={styles.profile}
              to={auth.status === 'authenticated' ? '/profile' : '/login'}
            >
              <span className={styles.avatar} aria-hidden="true">
                FI
              </span>
              <span>{auth.username ?? 'Accedi'}</span>
            </Link>
            {auth.status === 'authenticated' && (
              <button className={styles.logout} onClick={auth.logout}>
                Esci
              </button>
            )}
          </div>
        </header>
        <main className={styles.main}>{children}</main>
      </div>
    </div>
  );
}
