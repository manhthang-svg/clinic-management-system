import * as React from 'react'
import { cn } from '../../utils/utils'

export const Alert = React.forwardRef(({ className, variant = 'default', ...props }, ref) => {
  const variants = {
    default: 'bg-[#f4f8f7] text-[#142534] border-[#e3ece9]',
    destructive: 'bg-[#fdece8] text-[#d9534f] border-[#fbc9c0]',
    success: 'bg-[#d8f8f3] text-[#0c7a6d] border-[#a7f3e5]',
    warning: 'bg-[#fef6e4] text-[#b45309] border-[#fde68a]',
    info: 'bg-[#e0f2fe] text-[#0369a1] border-[#bae6fd]',
  }

  return (
    <div
      ref={ref}
      role="alert"
      className={cn(
        'relative w-full rounded-xl border p-4 text-sm flex items-start gap-3',
        variants[variant] || variants.default,
        className,
      )}
      {...props}
    />
  )
})
Alert.displayName = 'Alert'

export const AlertDescription = React.forwardRef(({ className, ...props }, ref) => (
  <div
    ref={ref}
    className={cn('text-sm leading-relaxed font-medium', className)}
    {...props}
  />
))
AlertDescription.displayName = 'AlertDescription'
