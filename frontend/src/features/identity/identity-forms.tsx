import type { ComponentProps, FormEvent, ReactNode } from 'react';
import { useState } from 'react';
import { KeyRound, ShieldCheck } from 'lucide-react';
import { ApiError, createFirstAdmin, login, type AuthResponse } from '@/api';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';

export function SetupForm({ onSetup }: { onSetup: (auth: AuthResponse) => void }) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);

    try {
      onSetup(await createFirstAdmin({ username, password }));
    } catch (error) {
      setError(asErrorMessage(error));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <AuthCard
      description="Set up the first local administrator."
      icon={<ShieldCheck className="h-5 w-5" aria-hidden />}
      title="Create admin account"
    >
      <form className="grid gap-4" onSubmit={submit}>
        <Field autoComplete="username" label="Username" name="username" onChange={setUsername} value={username} />
        <Field
          autoComplete="new-password"
          label="Password"
          minLength={12}
          name="password"
          onChange={setPassword}
          type="password"
          value={password}
        />
        {error && <Alert>{error}</Alert>}
        <Button type="submit" disabled={submitting}>{submitting ? 'Creating...' : 'Create admin'}</Button>
      </form>
    </AuthCard>
  );
}

export function LoginForm({
  notice,
  onLogin
}: {
  notice?: string;
  onLogin: (auth: AuthResponse) => void;
}) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);

    try {
      onLogin(await login({ username, password }));
    } catch (error) {
      setError(asErrorMessage(error));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <AuthCard
      icon={<KeyRound className="h-5 w-5" aria-hidden />}
      title="Sign in"
    >
      {notice && <Alert className="mb-4">{notice}</Alert>}
      <form className="grid gap-4" onSubmit={submit}>
        <Field autoComplete="username" label="Username" name="username" onChange={setUsername} value={username} />
        <Field
          autoComplete="current-password"
          label="Password"
          name="password"
          onChange={setPassword}
          type="password"
          value={password}
        />
        {error && <Alert>{error}</Alert>}
        <Button type="submit" disabled={submitting}>{submitting ? 'Signing in...' : 'Sign in'}</Button>
      </form>
    </AuthCard>
  );
}

function AuthCard({
  children,
  description,
  icon,
  title
}: {
  children: ReactNode;
  description?: string;
  icon: ReactNode;
  title: string;
}) {
  return (
    <main className="grid min-h-screen place-items-center bg-background p-6 text-foreground">
      <Card className="w-full max-w-md">
        <CardHeader>
          <div className="mb-1 flex h-10 w-10 items-center justify-center rounded-md bg-primary/10 text-primary">
            {icon}
          </div>
          <CardTitle>{title}</CardTitle>
          {description && <CardDescription>{description}</CardDescription>}
        </CardHeader>
        <CardContent>{children}</CardContent>
      </Card>
    </main>
  );
}

function Field({
  label,
  name,
  onChange,
  value,
  ...props
}: Omit<ComponentProps<typeof Input>, 'onChange'> & {
  label: string;
  name: string;
  onChange: (value: string) => void;
  value: string;
}) {
  return (
    <Label>
      {label}
      <Input
        name={name}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        required
        {...props}
      />
    </Label>
  );
}

function asErrorMessage(error: unknown): string {
  if (error instanceof ApiError && error.status === 401) {
    return 'Username or password was not accepted.';
  }

  if (error instanceof ApiError && error.status === 409) {
    return 'Setup has already been completed.';
  }

  return 'Something went wrong. Please try again.';
}
