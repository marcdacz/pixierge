/// <reference types="node" />

import { render, screen } from '@testing-library/react';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, test } from 'vitest';
import { TokenPreview } from '@/design-system/foundations/token-preview';

type Rgb = { r: number; g: number; b: number; alpha: number };

const styles = readFileSync(resolve(process.cwd(), 'src/styles.css'), 'utf8');

describe('design-system tokens', () => {
  test('token preview renders the semantic foundation surface', () => {
    render(<TokenPreview />);

    expect(screen.getByRole('heading', { name: 'Foundation preview' })).toBeInTheDocument();
    expect(screen.getByText('Canvas')).toBeInTheDocument();
    expect(screen.getByText('Danger')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Primary action' })).toBeInTheDocument();
  });

  test.each([
    ['dark content on canvas', ':root', '--content', '--canvas', 7],
    ['dark muted content on canvas', ':root', '--content-muted', '--canvas', 4.5],
    ['dark content on surface', ':root', '--content', '--surface', 7],
    ['dark action content on action', ':root', '--action-content', '--action', 4.5],
    ['dark warning content on warning surface', ':root', '--status-warning-content', '--status-warning-surface', 4.5],
    ['dark danger content on danger surface', ':root', '--status-danger-content', '--status-danger-surface', 4.5],
    ['light content on canvas', "[data-theme='light']", '--content', '--canvas', 7],
    ['light muted content on canvas', "[data-theme='light']", '--content-muted', '--canvas', 4.5],
    ['light content on surface', "[data-theme='light']", '--content', '--surface', 7],
    ['light action content on action', "[data-theme='light']", '--action-content', '--action', 4.5],
    [
      'light warning content on warning surface',
      "[data-theme='light']",
      '--status-warning-content',
      '--status-warning-surface',
      4.5
    ],
    [
      'light danger content on danger surface',
      "[data-theme='light']",
      '--status-danger-content',
      '--status-danger-surface',
      4.5
    ]
  ])('%s meets contrast requirements', (_, selector, foregroundToken, backgroundToken, minimumRatio) => {
    const tokens = tokensForSelector(selector);
    const foreground = resolveTokenColor(foregroundToken, tokens);
    const background = resolveTokenColor(backgroundToken, tokens);

    expect(contrastRatio(foreground, background)).toBeGreaterThanOrEqual(minimumRatio);
  });
});

function tokensForSelector(selector: string) {
  const rootTokens = parseBlock(':root');
  return selector === ':root' ? rootTokens : { ...rootTokens, ...parseBlock(selector) };
}

function parseBlock(selector: string) {
  const escaped = selector.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const match = new RegExp(`${escaped}\\s*\\{(?<body>[\\s\\S]*?)\\n\\}`, 'm').exec(styles);
  const body = match?.groups?.body;
  if (!body) {
    throw new Error(`Could not find CSS block for ${selector}`);
  }
  return Object.fromEntries(
    Array.from(body.matchAll(/(--[a-z0-9-]+):\s*([^;]+);/gi)).map((tokenMatch) => [
      tokenMatch[1]!,
      tokenMatch[2]!.trim()
    ])
  );
}

function resolveTokenColor(token: string, tokens: Record<string, string>, stack: string[] = []): Rgb {
  const raw = tokens[token];
  if (!raw) {
    throw new Error(`Missing token ${token}`);
  }
  return resolveColor(raw, tokens, stack.concat(token));
}

function resolveColor(raw: string, tokens: Record<string, string>, stack: string[]): Rgb {
  const value = raw.trim();
  const variable = /^var\((--[a-z0-9-]+)\)$/i.exec(value)?.[1];
  if (variable) {
    if (stack.includes(variable)) {
      throw new Error(`Circular token reference: ${stack.join(' -> ')} -> ${variable}`);
    }
    return resolveTokenColor(variable, tokens, stack);
  }

  if (value.startsWith('#')) {
    return hexToRgb(value);
  }

  const rgb = /^rgb\((\d+)\s+(\d+)\s+(\d+)(?:\s+\/\s+([0-9.]+))?\)$/i.exec(value);
  if (rgb) {
    const alpha = rgb[4] ? Number(rgb[4]) : 1;
    const foreground = { r: Number(rgb[1]), g: Number(rgb[2]), b: Number(rgb[3]), alpha };
    return alpha < 1 ? blend(foreground, resolveTokenColor('--canvas', tokens)) : foreground;
  }

  throw new Error(`Unsupported color value ${value}`);
}

function hexToRgb(hex: string): Rgb {
  const normalized = hex.slice(1);
  if (!/^[0-9a-f]{6}$/i.test(normalized)) {
    throw new Error(`Unsupported hex color ${hex}`);
  }
  return {
    r: Number.parseInt(normalized.slice(0, 2), 16),
    g: Number.parseInt(normalized.slice(2, 4), 16),
    b: Number.parseInt(normalized.slice(4, 6), 16),
    alpha: 1
  };
}

function blend(foreground: Rgb, background: Rgb): Rgb {
  return {
    r: foreground.r * foreground.alpha + background.r * (1 - foreground.alpha),
    g: foreground.g * foreground.alpha + background.g * (1 - foreground.alpha),
    b: foreground.b * foreground.alpha + background.b * (1 - foreground.alpha),
    alpha: 1
  };
}

function contrastRatio(first: Rgb, second: Rgb) {
  const lighter = Math.max(relativeLuminance(first), relativeLuminance(second));
  const darker = Math.min(relativeLuminance(first), relativeLuminance(second));
  return (lighter + 0.05) / (darker + 0.05);
}

function relativeLuminance(color: Rgb) {
  const channels = [color.r, color.g, color.b].map((channel) => {
    const value = channel / 255;
    return value <= 0.03928 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4;
  });
  return 0.2126 * channels[0]! + 0.7152 * channels[1]! + 0.0722 * channels[2]!;
}
