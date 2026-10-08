import { FactSources, GameDescription } from '@jordylab-fe/gamecatalog/api';

/** What the "About" label says and the full ids behind it (shown on hover), built only from what the server reported. */
export interface DescriptionHeading {
  text: string;
  tooltip: string | null;
}

/** An id such as "provider/model-x" shows as "model-x": the provider prefix is dropped for display. */
export function displayModel(id: string): string {
  const slash = id.lastIndexOf('/');

  return slash >= 0 ? id.slice(slash + 1) : id;
}

/** The "ABOUT ·" label for a description (ui-design §7), with no vendor or model name written into the code. */
export function descriptionHeading(description: GameDescription): DescriptionHeading {
  if (description.source === 'STEAM') {
    return { text: 'description from Steam', tooltip: null };
  }
  const { model, requestedModel } = description;
  if (model && requestedModel) {
    return {
      text: `written by ${displayModel(model)} · picked by ${displayModel(requestedModel)}`,
      tooltip: `${model} · ${requestedModel}`,
    };
  }
  if (model) {
    return { text: `written by ${displayModel(model)}`, tooltip: model };
  }
  if (requestedModel) {
    return { text: `written by ${displayModel(requestedModel)} (model not reported)`, tooltip: requestedModel };
  }

  return { text: 'written by AI', tooltip: null };
}

/** The last line of the Spec sheet, or null when no source is known. */
export function factSourcesLine(sources: FactSources): string | null {
  const parts: string[] = [];
  if (sources.facts) {
    parts.push(`facts from ${sources.facts === 'STEAM' ? 'Steam' : 'AI'}`);
  }
  if (sources.multiplayer) {
    parts.push(`multiplayer from ${sources.multiplayer === 'STEAM' ? 'Steam' : 'IGDB'}`);
  }

  return parts.length > 0 ? `Source · ${parts.join(' · ')}` : null;
}
