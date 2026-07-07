import { Injectable } from '@angular/core';
import { qtiTransformItem } from '@citolab/qti-components/qti-transformers';
import { qtiTransform } from '@citolab/qti-convert/qti-transformer';

function removeDoubleSlashes(path: string): string {
  return path.replace(/([^:]\/)\/+/g, '$1');
}

function directoryForHref(href: string): string {
  const idx = href.lastIndexOf('/');
  return idx >= 0 ? href.slice(0, idx + 1) : '/';
}

function toAbsoluteLocalUrl(href: string): string {
  if (!href || /^https?:\/\//i.test(href) || href.startsWith('data:')) {
    return href;
  }
  const path = href.startsWith('/') ? href : `/${href}`;
  return removeDoubleSlashes(`${window.location.origin}${path}`);
}

function ensureAbsoluteDirectory(directory: string): string {
  const absolute = toAbsoluteLocalUrl(directory);
  return absolute.endsWith('/') ? absolute : `${absolute}/`;
}

function ensureAbsolutePackageBase(packageBase: string): string {
  const absolute = toAbsoluteLocalUrl(packageBase);
  return absolute.endsWith('/') ? absolute : `${absolute}/`;
}

function resolveAssetUrl(assetHref: string, itemBase: string): string {
  if (!assetHref || /^https?:\/\//i.test(assetHref) || assetHref.startsWith('data:')) {
    return assetHref;
  }
  try {
    return new URL(assetHref, itemBase).toString();
  } catch {
    return assetHref;
  }
}

function rewriteRelativeSources(document: Document, itemBase: string): void {
  for (const element of document.querySelectorAll('[src], [href]')) {
    for (const attribute of ['src', 'href'] as const) {
      const value = element.getAttribute(attribute);
      if (
        !value ||
        value.startsWith('#') ||
        /^https?:\/\//i.test(value) ||
        value.startsWith('data:')
      ) {
        continue;
      }
      element.setAttribute(attribute, resolveAssetUrl(value, itemBase));
    }
  }
}

@Injectable({ providedIn: 'root' })
export class QtiItemProcessingService {
  async fetchAndProcessItem(
    href: string,
    packageBaseHref = '/demo-package',
  ): Promise<DocumentFragment | null> {
    const relativeItemHref = href.startsWith('/')
      ? href
      : removeDoubleSlashes(`${packageBaseHref}/${href}`);
    const absoluteItemHref = toAbsoluteLocalUrl(relativeItemHref);

    const response = await fetch(absoluteItemHref);
    if (!response.ok) {
      return null;
    }

    const xml = await response.text();
    const packageBase = ensureAbsolutePackageBase(packageBaseHref);
    const itemBase = ensureAbsoluteDirectory(directoryForHref(absoluteItemHref));
    const itemPath = itemBase.replace(packageBase, '');

    let transformedContent = qtiTransform(xml)
      .qbCleanup()
      .objectToAudio()
      .objectToVideo()
      .objectToImg()
      .depConvertExtended()
      .minChoicesToOne()
      .mathml()
      .ssmlSubToSpan()
      .hideInputsForChoiceInteractionWithImages()
      .changeAssetLocation((assetHref) => resolveAssetUrl(assetHref, itemBase))
      .customInteraction(packageBase, itemPath)
      .xml();

    let doc = qtiTransformItem().parse(transformedContent);
    try {
      const pciDoc = await doc.configurePci(
        itemBase,
        async () => null as unknown as import('@citolab/qti-components/qti-transformers').ModuleResolutionConfig,
      );
      transformedContent = pciDoc.xml();
      doc = qtiTransformItem().parse(transformedContent);
    } catch {
      // Demo items typically have no PCI modules.
    }

    doc.fn((document) => {
      document.querySelectorAll('qti-item-body').forEach((element) => {
        element.classList.add('custom-qti-style', 'content');
      });
      document.querySelectorAll('[title]').forEach((element) => {
        element.removeAttribute('title');
      });
      document.querySelectorAll('qti-companion-materials-info, script').forEach((element) => {
        element.remove();
      });
      rewriteRelativeSources(document, itemBase);
      return document;
    });

    doc.path(itemBase);

    const inlined = await qtiTransform(doc.xml()).stylesheetsInline();
    doc = qtiTransformItem().parse(inlined.xml());
    doc.fn((document) => {
      rewriteRelativeSources(document, itemBase);
      return document;
    });
    doc.path(itemBase);

    return doc.htmlDoc();
  }
}
