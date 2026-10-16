import { useContext } from 'react';
import { AuthContext, type AuthValue } from './AuthContext';

export function useAuth(): AuthValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth deve essere usato dentro AuthProvider');
  return context;
}
