<template>
  <DialogShell
    :model-value="Boolean(target)"
    :title="t('detail.reportTitle')"
    :icon="Flag"
    tone="mint"
    :busy="busy"
    @close="$emit('close')"
  >
    <div class="report-reasons">
      <button
        v-for="(value,index) in ReportReasons"
        :key="value"
        type="button"
        :class="['reason-'+index%5,{selected:reason===value}]"
        :aria-pressed="reason===value"
        :disabled="busy"
        @click="reason=value"
      >
        {{ t('detail.reasons.'+value) }}
      </button>
    </div>
    <el-input
      v-model="note"
      type="textarea"
      :rows="3"
      resize="none"
      maxlength="200"
      show-word-limit
      :placeholder="t('detail.reportNote')"
      :disabled="busy"
    />
    <template #footer>
      <el-button
        :disabled="busy"
        @click="$emit('close')"
      >
        {{ t('detail.cancel') }}
      </el-button><el-button
        type="primary"
        :disabled="!reason"
        :loading="busy"
        @click="submit"
      >
        {{ t('detail.reportSubmit') }}
      </el-button>
    </template>
  </DialogShell>
</template>
<script src="./ReportDialog.js"></script>
<style lang="scss" scoped src="./ReportDialog.scss"></style>
