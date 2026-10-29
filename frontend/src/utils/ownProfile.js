/** Adapt the UC-04 API response to the account components' view model. */
export function ownProfileFromApi(profile) {
  if (!profile) return profile;

  return {
    ...profile,
    phone: profile.phoneNumber ?? profile.phone ?? '',
    title: profile.academicTitle ?? profile.title ?? '',
    practiceLicenseNo: profile.practiceLicenseNo ?? '',
  };
}

