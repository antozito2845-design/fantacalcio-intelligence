import { Link } from 'react-router-dom';

export function StatusPage({ title }: { title: string }) {
  return (
    <main className="status-page">
      <h1>{title}</h1>
      <p>Questa area rispetta le capability della sessione corrente.</p>
      <Link to="/">Torna alla dashboard</Link>
    </main>
  );
}
