import assert from 'node:assert/strict'
import { test } from 'node:test'
import { errorCodes } from '../src/api/errorCodes.ts'
import { describeLoginError } from '../src/auth/loginErrorMessage.ts'

test('로그인 오류 코드는 사용자 문장으로 바뀌고 모르는 코드는 기본 문장, 코드가 없으면 null 이다', () => {
  assert.match(describeLoginError(errorCodes.authConsentRequired), /동의/)
  assert.match(describeLoginError(errorCodes.authInvalidCredential), /만료/)
  assert.match(describeLoginError('AUTH-999-999'), /실패/)
  assert.equal(describeLoginError(null), null)
  assert.equal(describeLoginError(''), null)
})
