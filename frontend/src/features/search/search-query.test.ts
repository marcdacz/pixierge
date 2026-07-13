import { describe, expect, it } from 'vitest';
import {
  composeSearchQuery,
  emptyFilterState,
  filtersFromParsed,
  normalizeExtension,
  quoteIfNeeded,
  splitCommaValues,
  toggleMultiValue
} from './search-query';

describe('search-query helpers', () => {
  it('normalizes extensions to dotted form', () => {
    expect(normalizeExtension('JPG')).toBe('.jpg');
    expect(normalizeExtension('.png')).toBe('.png');
    expect(normalizeExtension('  .HEIC  ')).toBe('.heic');
    expect(normalizeExtension('')).toBe('');
  });

  it('composes structured filters and free text', () => {
    const query = composeSearchQuery({
      ...emptyFilterState('beach'),
      libraries: ['Events'],
      albums: ['Japan 2025'],
      is: ['starred'],
      extensions: ['.jpg', '.heic']
    });

    expect(query).toBe('library:Events album:"Japan 2025" extension:.jpg,.heic is:starred beach');
  });

  it('round-trips comma-separated and repeated extension clauses', () => {
    const fromRepeated = filtersFromParsed('', [
      { field: 'extension', value: 'jpg', negated: false },
      { field: 'extension', value: '.raw', negated: false }
    ]);
    expect(fromRepeated.extensions).toEqual(['.jpg', '.raw']);
    expect(composeSearchQuery(fromRepeated)).toBe('extension:.jpg,.raw');

    const fromComma = filtersFromParsed('', [
      { field: 'extension', value: '.jpg,.heic,raw', negated: false }
    ]);
    expect(fromComma.extensions).toEqual(['.jpg', '.heic', '.raw']);
    expect(composeSearchQuery(fromComma)).toBe('extension:.jpg,.heic,.raw');
  });

  it('round-trips comma-separated library album and tag values', () => {
    const fromRepeated = filtersFromParsed('', [
      { field: 'library', value: 'Events', negated: false },
      { field: 'library', value: 'Japan', negated: false },
      { field: 'album', value: 'Summer', negated: false },
      { field: 'album', value: 'Winter', negated: false },
      { field: 'tag', value: 'Family', negated: false },
      { field: 'tag', value: 'Holiday', negated: false }
    ]);
    expect(composeSearchQuery(fromRepeated)).toBe(
      'library:Events,Japan album:Summer,Winter tag:Family,Holiday'
    );

    const fromComma = filtersFromParsed('', [
      { field: 'library', value: 'Events,Japan', negated: false },
      { field: 'album', value: 'Summer,"Japan 2025"', negated: false },
      { field: 'tag', value: 'Family,Holiday', negated: false }
    ]);
    expect(fromComma.libraries).toEqual(['Events', 'Japan']);
    expect(fromComma.albums).toEqual(['Summer', 'Japan 2025']);
    expect(fromComma.tags).toEqual(['Family', 'Holiday']);
    expect(composeSearchQuery(fromComma)).toBe(
      'library:Events,Japan album:Summer,"Japan 2025" tag:Family,Holiday'
    );
  });

  it('splits quoted comma-separated values', () => {
    expect(splitCommaValues('Events,"Japan 2025",Vacation')).toEqual([
      'Events',
      'Japan 2025',
      'Vacation'
    ]);
  });

  it('round-trips parsed clauses into filter state', () => {
    const filters = filtersFromParsed('004', [
      { field: 'library', value: 'Events', negated: false },
      { field: 'is', value: 'starred', negated: false },
      { field: 'tag', value: 'Private', negated: true }
    ]);

    expect(filters.libraries).toEqual(['Events']);
    expect(filters.is).toEqual(['starred']);
    expect(filters.freeText).toBe('004');
    expect(filters.negated).toEqual([{ field: 'tag', value: 'Private', negated: true }]);
    expect(composeSearchQuery(filters)).toBe('library:Events is:starred -tag:Private 004');
  });

  it('quotes values with whitespace and toggles multi-select values', () => {
    expect(quoteIfNeeded('Japan 2025')).toBe('"Japan 2025"');
    expect(toggleMultiValue(['Family'], 'Holiday', true)).toEqual(['Family', 'Holiday']);
    expect(toggleMultiValue(['Family', 'Holiday'], 'family', false)).toEqual(['Holiday']);
  });
});
