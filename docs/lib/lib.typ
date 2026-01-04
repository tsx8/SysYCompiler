// From https://github.com/OrangeX4/typst-ori, Modified

#import "@preview/numbly:0.1.0": numbly
#import "@preview/tablem:0.3.0": tablem, three-line-table
#import "@preview/mitex:0.2.5": *
#import "@preview/cmarker:0.1.6": render as cmarker-render
#import "@preview/lovelace:0.3.0": *
#let md = cmarker-render.with(math: mitex)

#let default-font = (
  main: "IBM Plex Serif",
  mono: "JetBrainsMono NFM",
  cjk: "Source Han Serif SC",
  emph: "Cambria",
  emph-cjk: "KaiTi",
  math: "Cambria Math",
  math-cjk: "Source Han Serif SC",
)

// Definitions for math
#let prox = math.op("prox")
#let proj = math.op("proj")
#let argmax = math.op("argmax", limits: true)
#let argmin = math.op("argmin", limits: true)

#let Input = [*Input:*]
#let Output = [*Output:*]
#let If = [*if*]
#let Else = [*else*]
#let For = [*for*]
#let While = [*while*]
#let Break = [*break*]
#let And = [*and*]
#let Or = [*or*]
#let To = [*to*]
#let Do = [*do*]
#let Then = [*then*]
#let End = [*end*]
#let Return = [*return*]
#let True = [*true*]
#let False = [*false*]
#let Eq = $<-$

#let algo(
  body,
  title: none,
  params: (),
  supplement: [Algorithm],
  ..args
) = {
  let full-title = if title != none {
    let params-content = params.join(", ")
    strong(supplement) + "  " + title + if params.len() > 0 {
      [(#params-content)]
    } else {
      []
    }
  } else {
    none
  }

  let stretcher = h(100%)

  show regex("[^[:alnum:]]"): it => $#it$

  figure(
    kind: "algorithm",
    supplement: supplement,
    // placement: auto,

    pseudocode-list(
      booktabs: true,
      stroke: none,
      // indentation: 0.5em,
      hooks: .8em,
      ..if full-title != none {(title: full-title)},
      ..args,
      body + [#stretcher]
    )
  )
}

/// 模板的核心类，规范了文档的格式。
/// - media (string): 媒体类型，可选值为 `"screen"` 和 `"print"`。默认为 `"print"`。
/// - theme (string): 主题，可选值为 `"light"` 和 `"dark"`。默认为 `"light"`。
/// - size (length): 字体大小。默认为 `11pt`。
/// - screen-size (length): 屏幕字体大小。默认为 `11pt`。
/// - title (string): 文档的标题。
/// - author (string): 作者。
/// - subject (string): 课程名。
/// - semester (string): 学期。
/// - date (datetime): 时间。
/// - font (object): 字体。默认为 `default-font`。如果你想使用不同的字体，可以传入一个字典，包含 `main`、`mono`、`cjk`、`math` 和 `math-cjk` 字段。
/// - lang (string): 语言。默认为 `zh`。
/// - region (string): 地区。默认为 `cn`。
/// - body (content): 文档的内容。
/// -> content
#let ori(
  media: "print",
  theme: "light",
  size: 11pt,
  screen-size: 11pt,
  title: none,
  author: none,
  number: none,
  subject: none,
  semester: none,
  date: none,
  font: default-font,
  lang: "zh",
  region: "cn",
  first-line-indent: (amount: 0pt, all: false),
  maketitle: false,
  makeoutline: false,
  outline-depth: 2,
  body,
) = {
  assert(media == "screen" or media == "print", message: "media must be 'screen' or 'print'")
  assert(theme == "light" or theme == "dark", message: "theme must be 'light' or 'dark'")
  let page-margin = if media == "screen" { (x: 35pt, y: 35pt) } else { auto }
  let text-size = if media == "screen" { screen-size } else { size }
  let bg-color = if theme == "dark" { rgb("#1f1f1f") } else { rgb("#ffffff") }
  let text-color = if theme == "dark" { rgb("#ffffff") } else { rgb("#000000") }
  let raw-color = if theme == "dark" { rgb("#27292c") } else { rgb("#f0f0f0") }
  let font = default-font + font

  /// 设置字体。
  set text(
    font: ((name: font.main, covers: "latin-in-cjk"), font.cjk),
    size: text-size,
    fill: text-color,
    lang: lang,
    region: region,
  )
  show smartquote: set text(font: font.main)
  show emph: text.with(font: ((name: font.emph, covers: "latin-in-cjk"), font.emph-cjk), weight: "medium")
  show raw: set text(font: ((name: font.mono, covers: "latin-in-cjk"), font.cjk))
  show math.equation: set text(font: (
    (name: font.math, covers: "latin-in-cjk"), // 数学
    (name: font.math-cjk, covers: regex(".")), // 中文
    font.math, // 数学
  ))

  set math.cases(gap: .8em)

  /// 设置段落样式。
  set par(
    justify: true,
    first-line-indent: if first-line-indent == auto {
      (amount: 2em, all: true)
    } else {
      first-line-indent
    },
    leading: 0.8em
  )

  /// 设置标题样式。
  show heading: it => {
    show h.where(amount: 0.3em): none
    it
  }
  show heading: set block(spacing: 1.5em)

  show heading.where(level: 1): set block(above: 3em)

  set heading(numbering: numbly("{1:一}、", "{1}.{2}    ", "{1}.{2}.{3}    "))

  /// 设置代码块样式。
  show raw.where(block: false): body => box(
    fill: raw-color,
    inset: (x: 3pt, y: 0pt),
    outset: (x: 0pt, y: 3pt),
    radius: 2pt,
    {
      set par(justify: false)
      body
    },
  )
  show raw.where(block: true): body => block(
    width: 100%,
    fill: raw-color,
    outset: (x: 0pt, y: 4pt),
    inset: (x: 8pt, y: 4pt),
    radius: 4pt,
    {
      set par(justify: false)
      body
    },
  )

  /// 设置链接样式。
  show link: it => {
    if type(it.dest) == str {
      set text(fill: blue)
      it
    } else {
      it
    }
  }

  set figure(gap: 1em)

  show figure.where(kind: raw): it => {
    show raw.where(block: true): align.with(left)
    it
  }

  /// 设置 figure 样式。
  show figure.where(kind: table): set figure.caption(position: bottom)

  /// 设置列表样式。
  set list(indent: 6pt)
  set enum(indent: 6pt)
  set enum(numbering: numbly("{1:1}.", "{2:1})", "{3:a}."), full: true)

  /// 设置引用样式。
  set bibliography(style: "ieee")

  /// 基础设置。
  set document(title: title, author: if type(author) == str { author } else { () }, date: date)

  /// 标题页。
  if maketitle {
    // Title page
    align(center + top)[
      #v(20%)
      #text(2em, weight: 500, subject)
      #v(2em, weak: true)
      #text(2em, weight: 500, title)
      #v(2em, weak: true)
      #author
    ]
    pagebreak(weak: true)
  }

  /// 目录。
  if makeoutline {
    show heading: align.with(center)
    show outline.entry: set block(spacing: 1.2em)

    outline(depth: outline-depth, indent: 2em)
    pagebreak(weak: true)
  }

  /// 重置页面计数器。
  counter(page).update(1)

  /// 设置页面。
  set page(
    paper: "a4",
    header: {
      set text(0.9em)
      stack(
        spacing: 0.2em,
        grid(
          columns: (1fr, auto, 1fr),
          align(left, number + " " + author),
          align(center, title),
          align(right, strong(subject) + "  " + semester)
        ),
        v(0.3em),
        line(length: 100%, stroke: 1pt + text-color),
        v(.15em),
        line(length: 100%, stroke: .5pt + text-color),
      )
      // reset footnote counter
      counter(footnote).update(0)
    },
    fill: bg-color,
    numbering: "1",
    margin: page-margin,
  )

  body
}
