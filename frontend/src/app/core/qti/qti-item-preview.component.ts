import {
  AfterViewInit,
  Component,
  computed,
  CUSTOM_ELEMENTS_SCHEMA,
  ElementRef,
  effect,
  inject,
  input,
  OnDestroy,
  signal,
  ViewChild,
} from '@angular/core';
import { QtiItemProcessingService } from './qti-item-processing.service';

type ItemContainerElement = HTMLElement & { itemDoc?: DocumentFragment };
type QtiAssessmentItemElement = HTMLElement & { readonly?: boolean };

const MIN_ASPECT_RATIO = 4 / 3;

@Component({
  selector: 'app-qti-item-preview',
  standalone: true,
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  template: `
    <section class="qti-item-preview" [attr.aria-busy]="loading()">
      @if (loading()) {
        <div class="qti-item-preview__loading">
          <span class="qti-item-preview__spinner" aria-hidden="true"></span>
          Vraag wordt geladen...
        </div>
      } @else if (error()) {
        <p class="qti-item-preview__error">{{ error() }}</p>
      } @else {
        <div class="qti-item-preview__frame" #frame [style.height.px]="frameHeight()">
          <div
            class="qti-item-preview__scaled"
            [style.width.px]="baseWidth"
            [style.height.px]="contentHeight() || null"
            [style.transform]="'scale(' + scale() + ')'"
          >
            <div #content class="qti-item-preview__content" [style.width.px]="baseWidth">
              <qti-item #qtiItem style="display: block; width: 100%">
                <item-container #itemContainer inert style="display: block; width: 100%">
                  <template #styleTemplate></template>
                </item-container>
              </qti-item>
            </div>
          </div>
        </div>
      }
    </section>
  `,
  styleUrl: './qti-item-preview.component.scss',
})
export class QtiItemPreviewComponent implements AfterViewInit, OnDestroy {
  readonly href = input.required<string>();
  readonly packageBaseHref = input('/demo-package');

  private readonly qtiProcessing = inject(QtiItemProcessingService);

  @ViewChild('itemContainer') private itemContainerRef?: ElementRef<ItemContainerElement>;
  @ViewChild('styleTemplate') private styleTemplateRef?: ElementRef<HTMLTemplateElement>;
  @ViewChild('frame') private frameRef?: ElementRef<HTMLElement>;
  @ViewChild('content') private contentRef?: ElementRef<HTMLElement>;

  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly scale = signal(1);
  readonly contentHeight = signal(0);
  readonly containerWidth = signal(0);
  readonly baseWidth = 480;

  readonly frameHeight = computed(() => {
    const width = this.containerWidth() || this.baseWidth;
    const scale = this.scale();
    const contentH = this.contentHeight();
    const minFromAspect = width / MIN_ASPECT_RATIO;
    const scaledContent = contentH > 0 ? contentH * scale + 12 : 0;
    return Math.max(minFromAspect, scaledContent);
  });

  private itemDoc = signal<DocumentFragment | null>(null);
  private viewReady = signal(false);
  private resizeObserver?: ResizeObserver;

  constructor() {
    effect(() => {
      const href = this.href();
      const packageBaseHref = this.packageBaseHref();
      if (!href) return;

      this.loading.set(true);
      this.error.set(null);
      this.itemDoc.set(null);
      this.contentHeight.set(0);

      void this.qtiProcessing.fetchAndProcessItem(href, packageBaseHref).then((doc) => {
        if (!doc) {
          this.error.set('Vraag kon niet worden geladen.');
          this.loading.set(false);
          return;
        }
        this.itemDoc.set(doc);
        this.loading.set(false);
      });
    });

    effect(() => {
      const doc = this.itemDoc();
      const ready = this.viewReady();
      if (!ready || !doc) {
        return;
      }
      queueMicrotask(() => this.mountItemDoc(doc));
    });

    effect(() => {
      this.itemDoc();
      this.viewReady();
      queueMicrotask(() => this.updateScale());
    });

    effect(() => {
      this.scale();
      queueMicrotask(() => this.measureContentHeight());
    });

    effect(() => {
      if (this.loading() || this.error() || !this.viewReady()) {
        return;
      }
      queueMicrotask(() => {
        this.setupResizeObserver();
        this.updateScale();
        this.measureContentHeight();
      });
    });
  }

  ngAfterViewInit(): void {
    this.injectPreviewStyles();
    this.viewReady.set(true);
    this.mountItemDoc(this.itemDoc());
    this.setupResizeObserver();
    window.addEventListener('resize', this.updateScale);
  }

  ngOnDestroy(): void {
    window.removeEventListener('resize', this.updateScale);
    this.resizeObserver?.disconnect();
  }

  private setupResizeObserver(): void {
    if (typeof ResizeObserver === 'undefined') {
      return;
    }

    if (!this.resizeObserver) {
      this.resizeObserver = new ResizeObserver(() => {
        this.updateScale();
        this.measureContentHeight();
      });
    }

    const frame = this.frameRef?.nativeElement;
    const container = this.itemContainerRef?.nativeElement;
    const content = this.contentRef?.nativeElement;

    if (frame) {
      this.resizeObserver.observe(frame);
    }
    if (container) {
      this.resizeObserver.observe(container);
    }
    if (content) {
      this.resizeObserver.observe(content);
    }
  }

  private injectPreviewStyles(): void {
    const template = this.styleTemplateRef?.nativeElement;
    if (!template || template.content.childNodes.length > 0) {
      return;
    }
    const style = document.createElement('style');
    style.textContent = `
      qti-assessment-item {
        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
        line-height: 1.6;
        color: #374151;
      }
      qti-item-body img {
        display: block;
        width: 100%;
        max-width: 32rem;
        height: auto;
        margin: 0.5rem 0;
        object-fit: contain;
      }
      [view] { display: none; }
      qti-outcome-declaration { display: none !important; }
    `;
    template.content.appendChild(style);
  }

  private mountItemDoc(doc: DocumentFragment | null): void {
    const container = this.itemContainerRef?.nativeElement;
    if (!container || !doc) {
      return;
    }
    this.contentHeight.set(0);
    container.itemDoc = doc.cloneNode(true) as DocumentFragment;
    this.makeReadOnly(container);
    queueMicrotask(() => {
      this.updateScale();
      this.measureContentHeight();
      this.observeContentElements();
      this.scheduleHeightMeasurements();
    });
  }

  private scheduleHeightMeasurements(): void {
    for (const delay of [50, 150, 400, 800]) {
      window.setTimeout(() => this.measureContentHeight(), delay);
    }
  }

  private observeContentElements(): void {
    const container = this.itemContainerRef?.nativeElement;
    const content = this.contentRef?.nativeElement;
    if (!this.resizeObserver) {
      return;
    }
    if (container) {
      this.resizeObserver.observe(container);
    }
    if (content) {
      this.resizeObserver.observe(content);
    }
  }

  private measureContentHeight(): void {
    const scale = this.scale();
    const elements = [
      this.itemContainerRef?.nativeElement,
      this.contentRef?.nativeElement,
    ].filter((element): element is HTMLElement => Boolean(element));

    let nextHeight = 0;
    for (const element of elements) {
      const rectHeight = element.getBoundingClientRect().height;
      const unscaledRectHeight = scale > 0 ? rectHeight / scale : rectHeight;
      nextHeight = Math.max(nextHeight, unscaledRectHeight, element.scrollHeight);
    }

    // Direct set (not grow-only) so the frame can shrink again after a resize;
    // the ResizeObserver and staged measurements grow it back during progressive load.
    if (nextHeight > 0) {
      this.contentHeight.set(nextHeight);
    }
  }

  private makeReadOnly(container: ItemContainerElement): void {
    let attempts = 0;
    const tick = () => {
      const assessmentItem = this.findAssessmentItem(container.shadowRoot ?? container);
      if (assessmentItem) {
        assessmentItem.readonly = true;
        this.measureContentHeight();
        return;
      }
      attempts += 1;
      if (attempts < 30) {
        window.setTimeout(tick, 50);
      }
    };
    queueMicrotask(() => requestAnimationFrame(tick));
  }

  private findAssessmentItem(root: ParentNode | null): QtiAssessmentItemElement | null {
    if (!root) return null;
    const direct = root.querySelector('qti-assessment-item');
    if (direct) return direct as QtiAssessmentItemElement;

    for (const element of root.querySelectorAll('*')) {
      if (element.shadowRoot) {
        const nested = this.findAssessmentItem(element.shadowRoot);
        if (nested) return nested;
      }
    }
    return null;
  }

  private updateScale = (): void => {
    const frame = this.frameRef?.nativeElement;
    if (!frame) return;
    this.containerWidth.set(frame.clientWidth);
    const next = Math.min(1, frame.clientWidth / this.baseWidth);
    this.scale.set(next > 0 ? next : 1);
  };
}
