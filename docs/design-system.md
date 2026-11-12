# M Bank design system

Status: in use. Home, Transactions, Send, Top up, Cards, Savings, Statements,
Profile, Security and the admin pages follow it. Accounts, Analytics, Payees,
Requests, Scheduled, Notifications and Support still use the older layout.

## Idea

A quiet ledger. A bank statement is already a good design: dates, descriptions
and right-aligned amounts between thin rules. The interface stays close to
that. It is ink on warm paper, and colour appears only when it means something.

The old design put every block in a rounded white card, gave each category its
own colour and animated the balance. That is what made it look generated. Here
the balance is plain text, lists sit directly on the page, and one green and
one red carry all the meaning colour has to carry.

## Colour

| Token | Light | Dark | Used for |
|---|---|---|---|
| `--bg` | `#F6F5F1` | `#121210` | The page |
| `--surface` | `#FFFFFF` | `#1B1A18` | A panel, only where grouping needs one |
| `--surface-sunken` | `#EFEDE7` | `#0E0E0C` | Inputs, hover and pressed states |
| `--line` / `--line-strong` | `#E3E0D8` / `#CDC9BE` | `#2A2927` / `#3D3C38` | Row dividers / control outlines |
| `--ink` | `#171612` | `#F4F2EC` | Text, amounts, primary buttons |
| `--ink-2`, `--ink-3` | `#47453F`, `#6A675F` | `#B9B6AD`, `#8F8C83` | Body copy; labels and metadata |
| `--ink-4` | `#A29E94` | `#5F5D57` | Placeholders and disabled text only |
| `--green` | `#0B6B43` | `#5CC694` | Money in, and the brand mark |
| `--red` | `#B3261E` | `#F0837A` | Errors and failed or negative states |
| `--chart-1` to `--chart-5` | neutral ramp | neutral ramp | Chart series, darkest first |

Rules:

- Green and red are never decoration. A green amount is money that arrived.
- Money out is ink, with a minus sign. It is not red: spending is not an error.
- Primary buttons are ink. Links are ink and underlined.
- There is no blue, violet, lime, coral or yellow, and no gradient.
- Contrast on `--bg`: `--ink` 16:1, `--ink-3` 5.2:1, `--green` 6.0:1, `--red` 6.0:1.

## Type

Inter, loaded as one variable font file and served by the app itself
(`@fontsource-variable/inter`), so there is no request to Google.

- Three weights: 400 text, 500 labels, amounts and buttons, 600 headings and the balance.
- Six sizes: 12 labels, 13 metadata, 15 body and rows, 18 section titles, 24 page titles, 40 the balance.
- Every amount, date and account number uses tabular numerals so columns line up.
- Sentence case everywhere. No all-caps labels.

## Space and shape

- Spacing is a 4px scale: 4, 8, 12, 16, 20, 28, 40, 56, 80.
- Three radii: 6px controls, 10px panels and dialogs, 16px card art and the top of a phone sheet.
- One shadow, used only by things that float: dialogs, menus and toasts.
- Lists and tables sit on the page between 1px dividers. A panel is used only
  when a group of controls has to read as one object, such as a form.

## Motion

Dialogs and toasts keep their short transitions, because they show where
something came from. Nothing else moves: no balance count-up, no fade on page
navigation, no success wave, no self-drawing tick, no scaling buttons.
`prefers-reduced-motion` is respected.

## Components

- **Button**: 44px tall. Primary is filled ink, one per view. Secondary is outlined.
- **Ledger row**: date, description, category, right-aligned amount. On a phone
  the date and category move under the description. The whole row opens the detail.
- **Status**: completed rows carry no label. In review, processing and reversed
  are small grey text under the amount; failed is red.
- **Empty state**: one plain sentence and, where useful, a link. No illustration.
- **Card art**: one flat colour in real card proportions, with brand, last four
  digits, holder and expiry.
- **Focus**: a 2px ink ring with a 2px gap, on every interactive element.

## Navigation

Five destinations: Home, Transactions, Payments, Cards, Savings. Everything
else lives in a profile menu that ends with Sign out. Desktop uses a sidebar,
phones use a bottom bar with the same five items. A small "Demo. No real
money." label stays visible in the shell.

## Home hierarchy

1. Available balance, as a plain 40px number, with savings as one line under it.
2. Actions: Send (primary), Request, Top up, Move to savings.
3. Money in and out this month.
4. Recent transactions as ledger rows.
5. Side column on wide screens: upcoming payments, then one card.

## Responsive

| Width | Layout |
|---|---|
| 1101px and up | Sidebar, main column, 320px side column |
| 768 to 1100px | Icon sidebar, one column, side column moves below |
| Under 768px | Bottom bar, one column, Send button full width, two-line ledger rows |

Checked at 375, 768 and 1440px: no horizontal overflow on Home.

## What is in the code now

- `frontend/src/styles/tokens.css`: the tokens above, plus legacy aliases that
  the older screens still use.
- `frontend/src/components/TransactionList.tsx`: the ledger row list.
- `frontend/src/pages/cards/CardSettings.tsx`: the settings list pattern.
- `frontend/src/utils/money.ts`: money arithmetic in integer cents.
- Screenshots: `docs/screenshots/proposal/` (the sample) and `docs/screenshots/after/`.

## Open questions

1. Keep the dark theme? It works on Home, but doubles the checking for every
   rebuilt screen. The alternative is light only.
2. Category icons are gone from transaction rows; the category is a word. Say
   if you want a small monochrome icon back.
3. Pending and held states are grey, not amber, to keep to one green and one
   red. Say if you want a third state colour.
