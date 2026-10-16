import { ArrowLeft } from 'lucide-react';
import { useLocation, useNavigate, type To } from 'react-router-dom';
import styles from './BackButton.module.css';

interface BackButtonProps {
  fallbackTo: To;
  label?: string;
}

interface NavigationState {
  from?: unknown;
}

export function BackButton({ fallbackTo, label = 'Indietro' }: BackButtonProps) {
  const navigate = useNavigate();
  const location = useLocation();
  const state = location.state as NavigationState | null;
  const stateFallback =
    typeof state?.from === 'string' && state.from.startsWith('/') ? state.from : fallbackTo;

  const handleBack = () => {
    const hasPreviousEntry = location.key !== 'default' || window.history.length > 1;
    if (hasPreviousEntry) void navigate(-1);
    else void navigate(stateFallback);
  };

  return (
    <button className={styles.backButton} type="button" onClick={handleBack} aria-label={label}>
      <ArrowLeft size={17} aria-hidden="true" />
      <span>{label}</span>
    </button>
  );
}
