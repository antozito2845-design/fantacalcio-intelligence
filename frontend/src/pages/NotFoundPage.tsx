import { Link } from 'react-router-dom';

export function NotFoundPage() {
  return (
    <main className="status-page">
      <h1>Pagina non trovata</h1>
      <p>La risorsa richiesta non esiste.</p>
      <Link to="/">Torna alla dashboard foundation</Link>
    </main>
  );
}
