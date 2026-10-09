import { useState } from 'react'
import { formatDateTime, formatSize } from '../lib/format'
import { useStore } from '../store'

export function InvoicePage() {
  const { invoices, addInvoices, removeInvoice, notify } = useStore()
  const [over, setOver] = useState(false)
  const [account, setAccount] = useState('')
  const [secret, setSecret] = useState('')
  const [show, setShow] = useState(false)

  async function take(list: File[]) {
    const accepted: File[] = []
    for (const file of list) {
      if (!file.name.toLowerCase().endsWith('.xml')) {
        notify(`${file.name} 不是 XML`)
        continue
      }
      const head = await file.slice(0, 400).text()
      if (!head.includes('<')) {
        notify(`${file.name} 内容不像 XML`)
        continue
      }
      accepted.push(file)
    }
    if (accepted.length) {
      addInvoices(accepted)
      notify(`已放入 ${accepted.length} 个未签章文件`)
    }
  }

  return (
    <div className="page">
      <header className="page-head">
        <div>
          <h1>发票助手</h1>
          <p className="lede">把没有签章的 XML 发票拖进来，签完后下载带数字签名的文件。税务局登录还没接，所以现在不能真正签名。</p>
        </div>
      </header>

      <ol className="steps">
        <li className="is-done">
          <span>1</span>
          <div>
            <strong>放入未签章 XML</strong>
            <p>可以一次拖多个。</p>
          </div>
        </li>
        <li>
          <span>2</span>
          <div>
            <strong>登录电子税务局</strong>
            <p>账号密码入口已留好，尚未连接。</p>
          </div>
        </li>
        <li>
          <span>3</span>
          <div>
            <strong>下载已签名 XML</strong>
            <p>签名成功后按原文件名加前缀下载。</p>
          </div>
        </li>
      </ol>

      <div className="invoice-layout">
        <section>
          <label
            className={over ? 'drop is-over drop-tall' : 'drop drop-tall'}
            onDragOver={(event) => {
              event.preventDefault()
              setOver(true)
            }}
            onDragLeave={() => setOver(false)}
            onDrop={(event) => {
              event.preventDefault()
              setOver(false)
              void take([...event.dataTransfer.files])
            }}
          >
            <strong>拖入未签章的 XML 发票</strong>
            <span>只会留在这台浏览器里，不会上传。</span>
            <em>或点击选择文件</em>
            <input
              type="file"
              accept=".xml,text/xml,application/xml"
              multiple
              onChange={(event) => {
                void take([...(event.target.files ?? [])])
                event.target.value = ''
              }}
            />
          </label>
          <p className="fine">
            没有文件可以先{' '}
            <a href="/sample-invoice.xml" download>
              下载一份示例 XML
            </a>
            ，它不是真实发票。
          </p>
          <div className="panel file-list">
            {invoices.length === 0 ? (
              <p className="empty">还没有待签章的文件。</p>
            ) : (
              invoices.map((file) => (
                <article key={file.id} className="file-row">
                  <div>
                    <strong>{file.name}</strong>
                    <p className="fine">
                      {formatSize(file.size)} · {formatDateTime(file.uploadedAt)} · 未签章
                    </p>
                    <p className="fine">预计下载：已签名-{file.name}</p>
                  </div>
                  <div className="row-actions">
                    <button type="button" className="btn btn-ghost btn-small" disabled>
                      下载
                    </button>
                    <button type="button" className="btn btn-ghost btn-small" onClick={() => removeInvoice(file.id)}>
                      移除
                    </button>
                  </div>
                </article>
              ))
            )}
          </div>
        </section>

        <form
          className="panel tax-card"
          onSubmit={(event) => {
            event.preventDefault()
            if (!account.trim() || !secret) {
              notify('请先填写税务局账号和密码，这一步目前只是占位')
              return
            }
            if (invoices.length === 0) {
              notify('还没有可签章的 XML')
              return
            }
            notify('电子税务局尚未接入，不会发送账号密码，也不能生成数字签名')
          }}
        >
          <p className="kicker">预留</p>
          <h2>电子税务局</h2>
          <p className="lede">签章需要登录税务局网站。账号和密码只留在这个页面，刷新就没了，不会保存，也不会提交。</p>
          <label className="field">
            <span>账号或纳税人识别号</span>
            <input value={account} onChange={(event) => setAccount(event.target.value)} autoComplete="off" />
          </label>
          <label className="field">
            <span className="password-line">
              密码
              <button type="button" className="text-btn" onClick={() => setShow((value) => !value)}>
                {show ? '隐藏' : '显示'}
              </button>
            </span>
            <input
              type={show ? 'text' : 'password'}
              value={secret}
              onChange={(event) => setSecret(event.target.value)}
              autoComplete="new-password"
            />
          </label>
          <button type="submit" className="btn btn-primary btn-block" disabled={invoices.length === 0}>
            登录并下载已签名 XML
          </button>
          <p className="fine">按钮在放入文件后可点，用来走通界面。真正的签名等税务局登录方式确定后再做。</p>
        </form>
      </div>
    </div>
  )
}
