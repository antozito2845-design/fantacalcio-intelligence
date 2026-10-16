import { Eye, EyeOff, LogIn } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '@/auth/useAuth';
import { Button, Input } from '@/components/ui';
import styles from './LoginPage.module.css';

export function LoginPage() {
  const auth = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState<string>();

  async function submit(event: FormEvent) {
    event.preventDefault();
    setError(undefined);
    try {
      await auth.login(username, password);
      const target = (location.state as { from?: string } | null)?.from ?? '/';
      void navigate(target, { replace: true });
    } catch (reason) {
      const kind =
        reason && typeof reason === 'object' && 'kind' in reason ? reason.kind : undefined;
      if (kind === 'unauthorized') setError('Credenziali non valide.');
      else if (kind === 'forbidden') setError('Accesso non consentito.');
      else setError('Servizio non raggiungibile. Riprova quando il backend è disponibile.');
    }
  }

  return (
    <main className={styles.page}>
      <section className={styles.loginCard} aria-labelledby="login-title">
        <div className={styles.brand}>
          <span>FI</span>
          <p>
            Fantacalcio
            <br />
            Intelligence
          </p>
        </div>
        <p className={styles.eyebrow}>Sessione protetta</p>
        <h1 id="login-title">Accedi al workspace</h1>
        <p className={styles.intro}>
          Le credenziali HTTP Basic restano esclusivamente in memoria e vengono eliminate al logout
          o al refresh.
        </p>
        <form onSubmit={(event) => void submit(event)} className={styles.form}>
          <Input
            label="Username"
            value={username}
            onChange={(event) => setUsername(event.target.value)}
            autoComplete="username"
            required
          />
          <div className={styles.passwordField}>
            <Input
              label="Password"
              type={showPassword ? 'text' : 'password'}
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              autoComplete="current-password"
              required
            />
            <button
              type="button"
              onClick={() => setShowPassword((value) => !value)}
              aria-label={showPassword ? 'Nascondi password' : 'Mostra password'}
            >
              {showPassword ? <EyeOff size={17} /> : <Eye size={17} />}
            </button>
          </div>
          {error && (
            <p className={styles.error} role="alert">
              {error}
            </p>
          )}
          <Button
            type="submit"
            loading={auth.status === 'authenticating'}
            disabled={!username || !password}
          >
            <LogIn size={17} /> Accedi
          </Button>
        </form>
        <button className={styles.back} onClick={() => void navigate('/')}>
          Continua senza autenticazione
        </button>
      </section>
    </main>
  );
}
