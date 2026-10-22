# Design System — Outpatient Clinic Management

This document defines the visual language and UI conventions of the `frontend/` application. It covers presentation only; business behavior, statuses, permissions, and rules live in [`docs/`](docs/) and are never defined here.

**Authority.** This document is the single source of truth for frontend visual design: tokens, typography, spacing, component appearance, interactive states, and accessibility requirements. Other files (including `CLAUDE.md` and `AGENTS.md`) link here instead of restating design rules. If another file appears to conflict with this one on a visual-design matter, this document wins and the other file should be corrected. Any change to design rules is made here first.

## How to Read This Document

Every rule carries one of these labels:

| Label | Meaning |
| --- | --- |
| **Existing** | Already implemented in `frontend/src`. Changing it changes current behavior or appearance. |
| **Proposed** | The approved target design. Not implemented yet; apply it only in a change that intentionally adopts it. |
| **TBD** | Depends on an open business or technical decision. Do not implement until the decision is recorded. |

**Adoption status.** The Mint & Navy tokens (adopted 2026-10-15, replacing Clinical Clarity), palette, typography fallbacks, focus treatment, form controls, buttons, banners, split login screen, password-change screen, and sidebar shell are implemented in `frontend/src`. Font delivery and proposed components not required by those screens remain unimplemented; see the [Adoption Plan](#adoption-plan).

## Design Direction

**Existing.** A professional, calm, minimal tool for clinic staff working long shifts at desktop workstations.

- **Readable first.** Dark text on light surfaces, generous line height for Vietnamese diacritics, no low-contrast "aesthetic" grays.
- **Soft mint canvas, white work surfaces, navy ink.** The page background is a pale gray-mint; tables, forms, and panels sit on white rounded cards; text and primary actions are deep navy; a light mint accent marks the brand, avatars, and selection.
- **Soft and rounded.** Generous radii (8px controls, 16px cards) and one subtle card shadow give a calm, modern feel without decoration.
- **Dense but orderly.** Optimized for patient tables, waiting queues, and clinical forms during the 07:00–09:00 reception peak (200–300 patients per day, `docs/business-context.md`). Density comes from tight, consistent spacing, never from smaller-than-legible text.
- **Color carries meaning, not decoration.** Navy marks the main action, teal marks focus, mint marks brand and selection. No gradients, glassmorphism, background images, or decorative illustrations; the only shadows are `--shadow-card` and `--shadow-dialog`.
- **Provenance.** The direction takes only a general color family (mint, gray-mint, navy) and a soft-card feel from healthcare dashboards the team reviewed. Layouts, components, and assets are original to this project; no third-party template files, layouts, or artwork are copied.
- **Desktop-first enterprise application.** Mouse and keyboard are the primary inputs.

## Current Implementation Baseline

**Existing.** What `frontend/src/styles/global.css` and the components render today:

| Aspect | Current value |
| --- | --- |
| Styling approach | One global stylesheet, `src/styles/global.css`; plain CSS, no UI or CSS framework, no CSS modules |
| Font | Be Vietnam Pro fallback stack; font delivery remains TBD |
| Text color | Neutral (navy) token `#1E3550` |
| Page background | Secondary token `#E8F0EF` |
| Feedback | Tokenized error, warning, and success banners with text and a left border |
| Application shell | White left sidebar (brand, account card, account actions) and a canvas content region; collapses to a top bar below 900px |
| Login | Navy brand panel beside a centered sign-in card; the panel collapses to a strip below 900px |
| Narrow forms | Tokenized white cards constrained by `--form-width` |
| Form layout | `.form` and `.field` grids with spacing tokens; labels remain above inputs |
| Inputs | Explicit size, padding, border, radius, focus, invalid, and disabled states |
| Buttons | Primary, secondary, and ghost variants with hover, active, focus, and disabled states |
| Focus | A 2px Primary `:focus-visible` outline with 2px offset |

Existing component conventions that remain valid under the new design:

- Semantic HTML only: `main`, `section`, `form`, `label`, `input`, `button`, `p`. No `div` buttons.
- Every input has a `<label htmlFor>` with a matching `id`.
- Forms use `noValidate`; validation messages are rendered by the component, not the browser.
- One form-level error message, rendered as `<p role="alert">` above the submit button.
- Success or progress notices use `<p role="status">`.
- Submit buttons are disabled while required fields are empty and while a request is in flight. While submitting, the label changes to a progress verb (`Đang đăng nhập…`, `Đang lưu…`).
- On a failed login, the password field is cleared.
- UI copy is Vietnamese. Role names are the labels in `src/app/App.tsx` (`Quản trị viên`, `Lễ tân`, `Bác sĩ`, `Kỹ thuật viên`, `Thu ngân`).

## Color

### Brand Palette — Official Tokens

**Existing.** Mint & Navy. These values are the official brand colors; derive states from the derived tokens below. Token names are kept from the previous palette so components did not change.

| Token | Value | Name | Intended use |
| --- | --- | --- | --- |
| `--color-primary` | `#0F7C72` | Teal | Focus rings, input border on focus, info banner bar, meaningful icons. Not a fill behind body text. |
| `--color-secondary` | `#E8F0EF` | Canvas | Application canvas; account card background in the sidebar. |
| `--color-tertiary` | `#D4E5E2` | Tertiary | Table header background, selected table row. |
| `--color-neutral` | `#1E3550` | Navy | Primary text and headings; login brand panel; brand-mark glyph; tooltip background. |
| `--color-white` | `#FFFFFF` | White | Cards, panels, tables, form fields, dialogs, sidebar; text on navy. |
| `--color-accent` | `#A6EBE0` | Mint | Brand mark, avatar, active navigation item, pressed secondary/ghost buttons, accent text on navy. Always with navy text on it; never as text on light surfaces. |
| `--color-accent-soft` | `#E0F5F1` | Mint soft | Hover background for secondary/ghost buttons; info banner background; caption text on navy. |

### Derived Tokens

**Existing.** Ratios are WCAG 2.x contrast against the listed background.

| Token | Value | Use | Contrast |
| --- | --- | --- | --- |
| `--color-primary-strong` | `#1E3550` | Primary button fill | White on it 12.50 |
| `--color-primary-hover` | `#15283E` | Primary button hover | White on it 14.95 |
| `--color-primary-active` | `#0D1B2B` | Primary button pressed | — |
| `--color-link` | `#0B665E` | Link text | On white 6.83; Canvas 5.90 |
| `--color-text-secondary` | `#46616A` | Helper text, captions, metadata, placeholder | On white ≥ 6; Canvas ≥ 5; Tertiary ≥ 4.5 |
| `--color-border-control` | `#6A8587` | Input borders, secondary button border | On white 3.95; Canvas 3.41 |
| `--color-border-subtle` | `#DBE7E5` | Card borders, dividers (decorative) | Not required to meet 3:1 |
| `--color-surface-hover` | `#F2F8F7` | Table row hover | Navy on it 11.63 |
| `--color-disabled-bg` | `#EDF2F2` | Disabled control background | — |
| `--color-disabled-text` | `#5D7374` | Disabled control text | 4.46 on disabled bg |
| `--color-danger` | `#B91C1C` | Error text, invalid border, destructive fill | On white 6.47; Canvas 5.59 |
| `--color-danger-hover` | `#A51A1A` | Destructive button hover | White on it 7.60 |
| `--color-danger-bg` | `#FDECEC` | Error banner background | — |
| `--color-success` | `#19693C` | Success text and icon | On white 6.72; Canvas 5.80 |
| `--color-success-bg` | `#E6F4EC` | Success banner background | — |
| `--color-warning` | `#8A5A00` | Warning text and icon | On white 5.93; Canvas 5.12 |
| `--color-warning-bg` | `#FFF4DB` | Warning banner background | — |
| `--color-info` | `#0B665E` | Informational text | See `link` |
| `--color-info-bg` | `#E0F5F1` | Informational banner background | — |

Navy `#1E3550` text contrast: 12.50 on white, 10.80 on Canvas, 9.58 on Tertiary, 9.27 on Mint — AAA everywhere it is used. Teal focus ring: 5.06 on white, 4.38 on Canvas, 3.88 on Tertiary — all ≥ 3:1.

### Color Rules

**Proposed.**

1. Body-size text (< 24px regular, < 18.66px bold) must be at least 4.5:1 against its background. On light surfaces use only `neutral`, `text-secondary`, `link`, `danger`, `success`, or `warning`; on navy use `white`, `accent`, or `accent-soft`.
2. Control boundaries, focus indicators, and meaningful icons must be at least 3:1 against adjacent colors.
3. Never use Secondary, Tertiary, Accent, or Accent soft as text on light surfaces or as the only boundary of a control.
4. Never convey state by color alone. Errors have text; selection has a bar plus background; priority has a text label; statuses have a text label.
5. Dark mode is **TBD** and not designed. Do not add dark-mode styles until requested.

## Typography

**Proposed.**

| Role | Family | Fallback stack |
| --- | --- | --- |
| Headings and body | **Be Vietnam Pro** | `'Be Vietnam Pro', system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif` |
| Technical labels and identifiers | **JetBrains Mono** | `'JetBrains Mono', ui-monospace, 'Cascadia Mono', Consolas, monospace` |

Be Vietnam Pro is designed for Vietnamese and renders stacked diacritics (ệ, ổ, ữ) cleanly.

**TBD — font delivery.** Loading the fonts requires either a self-hosted font package (a new dependency) or an external font service (a third-party request from a medical application, with privacy and Content-Security-Policy implications). Neither is approved. Until a decision is recorded, the fallback stacks apply and no dependency is added.

### Type Scale

Desktop-first; `rem` relative to a 16px root. Line heights are generous because Vietnamese diacritics extend above and below the x-height.

| Token | Size / line height | Weight | Use |
| --- | --- | --- | --- |
| `--font-size-page-title` | 24px / 32px | 600 | Page `h1` |
| `--font-size-section-title` | 20px / 28px | 600 | Section `h2`, dialog title |
| `--font-size-subsection` | 16px / 24px | 600 | `h3`, card titles |
| `--font-size-body` | 14px / 22px | 400 | Default body, form fields, table cells |
| `--font-size-body-strong` | 14px / 22px | 600 | Labels, table headers, emphasized values |
| `--font-size-caption` | 12px / 18px | 400 | Helper text, timestamps, metadata |
| `--font-size-mono` | 13px / 20px | 400–500 | Identifiers (JetBrains Mono) |

Rules:

- Minimum text size is 12px; never smaller, including in dense tables.
- Weights used: 400 and 600 only (500 permitted for monospace identifiers).
- Use `font-variant-numeric: tabular-nums` for numbers in tables, amounts, times, and queue numbers so columns align.
- Sentence case for headings, labels, and buttons. No all-caps text except where an identifier is inherently upper-case.

### Monospace Usage

**Proposed.** Use JetBrains Mono only for values that staff read character by character or compare across rows. Identifiers documented in `docs/business-context.md` include:

- registration code and queue number (BR-16);
- receipt number (BR-37);
- room code (BR-06);
- ICD-10 code (BR-31);
- username (BR-01).

Do not use monospace for names, labels, prose, or amounts of money. Do not display internal technical UUIDs to staff unless a use case requires it.

## Spacing, Sizing, and Shape

### Spacing Scale

**Proposed.** 4px base unit.

| Token | Value | Typical use |
| --- | --- | --- |
| `--space-1` | 4px | Icon-to-text gap, badge padding |
| `--space-2` | 8px | Label-to-input gap, compact cell padding |
| `--space-3` | 12px | Field-to-field gap in dense forms, table cell horizontal padding |
| `--space-4` | 16px | Card padding (dense), form field groups |
| `--space-5` | 24px | Card padding (default), gap between sections |
| `--space-6` | 32px | Page padding, gap between major regions |
| `--space-7` | 48px | Empty-state vertical padding |

**Existing** values map closely: `0.5rem` = `--space-2`, `0.75rem` = `--space-3`, `2rem` = `--space-6`. The existing button padding `0.6rem` (9.6px) is off-scale; the target button sizes below replace it.

### Control Sizes

**Proposed.**

| Size | Height | Horizontal padding | Use |
| --- | --- | --- | --- |
| Compact | 32px | 12px | Row actions inside tables, toolbars |
| Default | 40px | 16px | Forms, page actions |
| Large | 44px | 24px | Login and other single-task screens |

All interactive targets are at least 24×24 CSS px (WCAG 2.5.8); icon-only buttons are at least 32×32.

### Borders, Radius, and Elevation

**Proposed.**

| Token | Value | Use |
| --- | --- | --- |
| `--border-width` | 1px | All borders |
| `--border-width-focus` | 2px | Focus outline |
| `--radius-sm` | 8px | Inputs, buttons, banners |
| `--radius-md` | 16px | Cards, panels, dialogs, account card |
| `--radius-pill` | 999px | Avatars, status badges |
| `--shadow-card` | `0 1px 2px rgb(30 53 80 / 0.04), 0 4px 16px rgb(30 53 80 / 0.06)` | Cards on the canvas |
| `--shadow-dialog` | `0 12px 32px rgb(30 53 80 / 0.18)` | Dialogs and popovers |

Cards use a 1px `border-subtle` border plus `--shadow-card`. No other shadows.

## Interactive States

**Proposed.** Every interactive element defines all applicable states. Transitions, if any, are ≤ 150ms on `background-color`, `border-color`, and `color` only, and are disabled under `prefers-reduced-motion: reduce`.

### Focus

- Use `:focus-visible`, never remove focus outlines without a replacement.
- Style: `outline: 2px solid var(--color-primary); outline-offset: 2px;`
- Ring color `primary` (teal); contrast 5.06:1 on white, 4.38:1 on Canvas, 3.88:1 on Tertiary — all ≥ 3:1. The 2px offset places the ring against the surrounding surface, so it stays visible around filled buttons.
- Inputs additionally switch their border to `--color-primary` on focus.

### Buttons

| Variant | Default | Hover | Active (pressed) | Disabled |
| --- | --- | --- | --- | --- |
| Primary | Navy `primary-strong` fill, white text | `primary-hover` fill | `primary-active` fill | `disabled-bg` fill, `disabled-text`, `border-subtle` border |
| Secondary (outline) | White fill, `border-control` 1px border, `neutral` text | `accent-soft` fill | `accent` fill | `disabled-bg` fill, `disabled-text`, `border-subtle` border |
| Ghost | Transparent, `neutral` text | `accent-soft` fill | `accent` fill | `disabled-text`, no fill |
| Danger | `danger` fill, white text | `danger-hover` fill | `danger-hover` fill | As Primary disabled |

Rules:

- One Primary button per form or page region — the main action.
- Disabled buttons use `cursor: not-allowed` and keep the `disabled` attribute (not only a class). WCAG exempts disabled controls from contrast requirements, but the chosen pair (4.20:1) keeps them legible.
- While submitting, keep the button disabled and show the progress label (**Existing** convention).
- Destructive actions (for example locking an account, UC-10) use Danger and require confirmation per their use case.

### Text Inputs, Selects, and Text Areas

| State | Treatment |
| --- | --- |
| Default | White background, 1px `border-control`, `neutral` text, `text-secondary` placeholder |
| Hover | Border `neutral` |
| Focus | Border `primary` plus the focus outline |
| Invalid | 1px `danger` border, `aria-invalid="true"`, error text below the field in `danger` linked by `aria-describedby` |
| Disabled | `disabled-bg` background, `disabled-text` text, `border-subtle` border |
| Read-only | White background, `border-subtle` border, text remains `neutral` and selectable |

Placeholders are examples, never a replacement for the label.

### Links

`link` text, underlined. Hover: `neutral`. Visited links are not styled differently.

## Feedback and Messages

**Existing.**

- Form-level error: `<p role="alert">` in red.
- Success/progress notice: `<p role="status">`.
- Loading screen: `<p role="status">Đang tải…</p>`.

**Proposed.**

| Type | Text / icon | Background | Left border | ARIA |
| --- | --- | --- | --- | --- |
| Error | `danger` | `danger-bg` | 4px `danger` | `role="alert"` |
| Warning | `warning` (text may be `neutral` for long content) | `warning-bg` | 4px `warning` | `role="alert"` if it blocks work, otherwise `role="status"` |
| Success | `success` | `success-bg` | 4px `success` | `role="status"` |
| Info | `info` | `info-bg` | 4px `primary` | `role="status"` |

- Field-level errors appear under the field; a summary at the top is added only when a form has more than one invalid field.
- Every banner includes text. An icon is optional and decorative (`aria-hidden="true"`).
- Messages are short, specific Vietnamese sentences that say what happened and what to do, matching the existing tone (`Mật khẩu hiện tại không đúng.`).
- Never show raw API messages, stack traces, correlation IDs as primary content, tokens, or medical data in error messages. A support reference (`requestId`) may be shown as secondary, monospaced text if a use case asks for it.
- The forced password change notice (UC-3) is a blocking warning.

## Layout

**Proposed.** Desktop-first.

- Target viewport: 1280px and wider. Layouts must remain usable without horizontal page scroll down to 1024px. Tables may scroll horizontally inside their own container.
- Tablet and mobile support: **TBD**; not a design target.
- Page canvas: `secondary` background.
- Content sits in white cards/panels with a 1px `tertiary` border and `--radius-md`.
- Data-heavy pages (tables, queues) use the full available width. Single-task forms keep a readable width: 22rem for login and password (**Existing**), up to 40rem for multi-field clinical or administrative forms.
- Page structure: page title (`h1`) and primary page action on one row, then filters, then content.

### Application Shell

**Existing.**

- Left sidebar (`header.app-sidebar`, 248px, white, sticky): brand (mint tile with a navy plus mark, product name) at the top; account card at the bottom on a Canvas background with a mint initial avatar, full name, role label, `Đổi mật khẩu` (ghost) and `Đăng xuất` (secondary).
- Main region (`main.app-main`, canvas, max 64rem): page header with the `h1`, then banners and cards.
- Below 900px the sidebar becomes a top bar and the account card flattens into a row.

**TBD — navigation items.** Routing is undecided (`docs/architecture.md`). When decided, navigation goes in the sidebar between the brand and the account card, showing only the signed-in role's functions (BR-02, `docs/permission-matrix.md`). Proposed item style: 40px row, `--radius-sm`, navy text; hover `accent-soft`; active `accent` background, 600 weight, `aria-current="page"`.

### Login Screen

**Existing.** Two columns: a navy brand panel (`aside.login-brand`: brand, headline with a mint accent phrase, caption) and a centered white sign-in card. Below 900px the panel collapses to a brand strip above the card.

The UI hides functions a role cannot use, but the backend remains the authority for authorization.

## Components

**Existing** components are listed in `frontend/src` today: page (`main`), section with `h2`, labelled text field, submit button, form-level alert, status notice, loading state, and error boundary fallback. There is no shared component library yet; extract one only when a second feature needs the same element.

The components below are **Proposed** patterns, to be built in plain React and CSS when a use case needs them.

### Data Table

For patient lists, account lists (UC-5), and similar.

- Native `<table>` with `<caption>` (may be visually hidden), `<thead>`, `<th scope="col">`.
- Header: `tertiary` background, 600 weight, sticky when the table scrolls.
- Rows: white, 1px `border-subtle` dividers, 40px default height, 32px compact. No zebra striping.
- Row hover: `surface-hover`. Selected row: `tertiary` background plus a 3px `primary` left bar.
- Alignment: text left, numbers and amounts right with tabular numerals, identifiers in monospace.
- Row actions: compact ghost buttons at the row end; destructive actions not inline unless confirmed.
- Pagination, sorting, and filtering follow `docs/api-conventions.md`.
- Empty, loading, and error states render inside the table area (see below).

### Waiting Queue List

For examination, conclusion, and paraclinical waiting lists (BR-18).

- Order exactly as the API returns it; the UI never re-sorts a queue.
- Queue number in monospace, prominent (16px, 600).
- Priority patients (BR-14) show a text label `Ưu tiên` plus an icon, not color alone.
- The skipped list (BR-21) is a separate, clearly titled list, not a filter hidden in the main list.
- Call and accept actions follow the relevant use cases; button labels and flows are **TBD** until those use cases are specified.

### Status Badge

- Inline pill: `--radius-pill`, 12px text, 600 weight, `--space-1` vertical and `--space-2` horizontal padding, always with a text label.
- Available tones: neutral (`neutral` on `tertiary`), info, success, warning, danger (each token pair from [Feedback and Messages](#feedback-and-messages)).
- **TBD — status-to-tone mapping.** Visit, order, receipt, and account statuses are defined only in approved source-of-truth documents (`docs/state-transitions.md` is currently being rewritten). Assign tones per status when its labels are approved; do not create statuses or labels in the UI.

### Dialog

- Native `<dialog>` with `showModal()` for focus trapping and `Esc` to close.
- White surface, `--radius-md`, `--shadow-dialog`, max width 32rem for confirmations.
- Title as `h2`; actions bottom-right, primary action last, cancel as Secondary.
- Return focus to the triggering element on close.

### Form Layout

- Labels above fields (**Existing**).
- Required fields marked with text such as `(bắt buộc)`, not an asterisk alone. **TBD:** confirm wording with the team.
- Related fields grouped with `<fieldset>` and `<legend>`.
- Two-column layouts only for short related fields (for example date and time); clinical text areas take full width.
- Client-side validation mirrors backend rules for early feedback; the backend remains authoritative (**Existing** convention in `ChangePasswordForm`).

### Empty, Loading, and Error States

- Loading: text with `role="status"` (**Existing**); a skeleton is optional for tables. No spinners without text.
- Empty: a one-line explanation in `text-secondary` and, if the role can act, the action that fills it.
- Error: an error banner with a retry action where retrying is safe.

## Accessibility

**Proposed** baseline: WCAG 2.2 AA.

- Full keyboard operation; visible focus on every interactive element; logical tab order.
- Semantic landmarks: one `main`, `nav` for navigation, `header` for the top bar.
- Every input has a visible label; groups use `fieldset`/`legend`.
- Live regions: `role="alert"` for errors, `role="status"` for non-urgent updates (**Existing**).
- Do not convey information by color alone.
- Respect `prefers-reduced-motion`.
- Text must remain readable at 200% browser zoom.
- `lang="vi"` is the correct document language for the Vietnamese UI. `index.html` currently declares `lang="en"`; correcting it is a code change to make deliberately.

## Content and Language

- **Existing:** All UI copy is Vietnamese. Tests query elements by these Vietnamese labels, so changing copy requires updating tests.
- Buttons use a short verb phrase: `Đăng nhập`, `Đổi mật khẩu`, `Lưu`.
- Progress labels use `Đang …` with an ellipsis character (`…`).
- Dates and times display in Vietnamese format (`dd/MM/yyyy`, 24-hour `HH:mm`). **TBD:** confirm the clinic time zone handling (OQ-11 in `docs/business-context.md`).
- Money displays in VND without decimals, with a thousands separator (`150.000 ₫`). **TBD:** confirm the exact format with the team.
- Use the glossary terms in `docs/business-context.md` for domain vocabulary.

## Privacy in the UI

From `docs/security.md`:

- Never put patient data, medical data, or tokens in URLs, `localStorage`, `sessionStorage`, or IndexedDB.
- The access token stays in memory only (**Existing**, `session-manager.ts`).
- Avoid showing more personal or medical data than the screen's use case requires.

## Implementation Guidance

**Proposed.**

- Implement tokens as CSS custom properties on `:root` in `src/styles/global.css`, using the names in this document.
- **Existing:** the styling approach is plain CSS in `src/styles/global.css` with semantic HTML. Keep using it until a UI framework is adopted through the process below.
- Prefer element and role selectors with small, feature-scoped class names (the **Existing** pattern is `.login-page`, `.password-form`). Do not introduce a naming methodology or CSS modules without a decision.
- Never hard-code a color, font size, spacing, or radius value in a component once tokens exist; use the token.

### UI Framework Adoption

No UI component library, CSS framework, or CSS-in-JS library is currently adopted. Tailwind CSS, shadcn/ui, and similar tools are **not prohibited**; they may be adopted in the future, but only through this process:

1. **Explicit approval.** The project team explicitly approves adopting the specific tool for a stated, current need (`AGENTS.md`: no dependencies without a demonstrated need). Installing a package, generating components, or adding configuration does not count as approval.
2. **Compatibility evaluation**, recorded before any production code depends on the tool. It must confirm:
   - compatibility with the project's React, TypeScript (strict, `verbatimModuleSyntax`), Vite, ESLint, and Vitest versions in `frontend/package.json`;
   - that the Mint & Navy tokens in this document can be expressed exactly in the tool's theme or configuration, without changing any token value;
   - that WCAG 2.2 AA requirements in this document, including the contrast ratios and focus, disabled, and error states, are met or exceeded by the generated components;
   - coexistence with, or a migration path from, `src/styles/global.css` without unintended visual changes to existing screens;
   - that existing tests, which query by role and Vietnamese label, keep passing without being weakened;
   - the effect on bundle size and on the dependency footprint.
3. **Record the decision in this document**: the tool, version, scope, evaluation result, and the date. Update the **Existing** styling statement above at the same time.
4. **Adopt incrementally**, following the [Adoption Plan](#adoption-plan). Visual design rules stay in this document regardless of the tool; the tool implements them and never redefines them.

Until a decision is recorded here, do not add these tools to `package.json` or use them in `src/`.

## Adoption Plan

Adoption proceeds incrementally:

1. **Completed:** add the tokens as CSS custom properties.
2. **Completed:** switch existing screens to tokens and apply the palette, typography fallbacks, focus, and button/input states; verify the login, change-password, and signed-in screens.
3. **Completed 2026-10-15:** replace Clinical Clarity with Mint & Navy; adopt the split login screen and sidebar shell.
4. Decide font delivery (TBD above), then load Be Vietnam Pro and JetBrains Mono.
5. Build new proposed components only as use cases require them.

## Open Design Decisions

| Item | Status |
| --- | --- |
| Font delivery (self-hosted package vs external service) | **TBD** |
| UI framework (for example Tailwind CSS, shadcn/ui) | **None adopted**; possible after approval and evaluation ([UI Framework Adoption](#ui-framework-adoption)) |
| Routing and navigation items | **TBD** (`docs/architecture.md`); shell layout adopted |
| Status labels and badge tone mapping | **TBD** (pending approved state documents) |
| Required-field wording | **TBD** |
| Date, time zone, and currency formats | **TBD** |
| Tablet/mobile support | **TBD** |
| Dark mode | **TBD**, not planned |
