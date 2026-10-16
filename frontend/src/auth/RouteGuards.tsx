import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '@/auth/useAuth';

export function ProtectedRoute() {
  const auth = useAuth();
  const location = useLocation();
  return auth.status === 'authenticated' ? (
    <Outlet />
  ) : (
    <Navigate to="/login" state={{ from: location.pathname }} replace />
  );
}

export function AdminRoute() {
  const auth = useAuth();
  if (auth.status !== 'authenticated') return <Navigate to="/login" replace />;
  return auth.isAdmin ? <Outlet /> : <Navigate to="/access-denied" replace />;
}

export function PublicOnlyRoute() {
  const auth = useAuth();
  const location = useLocation();
  const target = (location.state as { from?: string } | null)?.from ?? '/';
  return auth.status === 'authenticated' ? <Navigate to={target} replace /> : <Outlet />;
}
